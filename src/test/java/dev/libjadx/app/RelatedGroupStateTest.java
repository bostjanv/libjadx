package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.mappings.MappingImportDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.probes.*;
import dev.libjadx.project.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelatedGroupStateTest {
	@TempDir Path dir;
	@Test void unsavedRestartDiscardReloadExplicitSaveAndMappingExportImport() throws Exception {
		var inputs=RelatedFixture.compile(dir);Path path=dir.resolve("project.jadx");NativeProjectDocument.newFromInputs(path,inputs).save();
		var disk=FileFingerprint.of(path);
		for(boolean reload:List.of(false,true)) {
			try(var runtime=RelatedGroupAdmissionTest.open(path)) {
				new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS)));
				RelatedGroupAdmissionTest.records(runtime,RelatedGroupAdmissionTest.FAMILY,RelatedGroupAdmissionTest.ALIAS);assertTrue(runtime.projectSnapshot().dirty());assertEquals(disk,FileFingerprint.of(path));
				if(reload) {
					var r=runtime.projectSnapshot().revisions();runtime.reloadProject(true,r.sessionId(),r.logicalRevision());
					assertFalse(runtime.projectSnapshot().dirty());assertEquals("joined",RelatedFixture.method(runtime.decompiler(),RelatedGroupAdmissionTest.SEED).getName());
					assertEquals(0,runtime.<Integer>withExclusiveEdit(c->c.codeDataCopy().getRenames().size()).intValue());
				}
			}
			try(var restarted=RelatedGroupAdmissionTest.open(path)) { assertEquals("joined",RelatedFixture.method(restarted.decompiler(),RelatedGroupAdmissionTest.SEED).getName());assertFalse(restarted.projectSnapshot().dirty()); }
		}
		Path export=dir.resolve("group.tiny");
		try(var runtime=RelatedGroupAdmissionTest.open(path)) {
			new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS)));
			var r=runtime.projectSnapshot().revisions();new MappingExportService(runtime).export(new MappingExportDtos.Request(export,"TINY_V2",r.sessionId(),r.logicalRevision()));
			assertEquals(4,Files.readString(export).split("\\tm\\t\\(I\\)I\\tjoined\\trenamedJoined",-1).length-1);
			runtime.saveProject(null,null);
		}
		try(var runtime=RelatedGroupAdmissionTest.open(path)) { RelatedGroupAdmissionTest.records(runtime,RelatedGroupAdmissionTest.FAMILY,RelatedGroupAdmissionTest.ALIAS); }
		Path imported=dir.resolve("imported.jadx");NativeProjectDocument.newFromInputs(imported,inputs).save();
		try(var runtime=RelatedGroupAdmissionTest.open(imported)) {
			var r=runtime.projectSnapshot().revisions();new MappingImportService(runtime).importMappings(new MappingImportDtos.Request(export,"TINY_V2","MERGE_FAIL_ON_CONFLICT",r.sessionId(),r.logicalRevision()));
			RelatedGroupAdmissionTest.records(runtime,RelatedGroupAdmissionTest.FAMILY,RelatedGroupAdmissionTest.ALIAS);
			assertEquals("NO_CHANGE",new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS))).outcome());
		}
	}

	@ParameterizedTest @ValueSource(strings={"input-before","input-load","mapping-before","mapping-load"})
	void externalBytesConflictWithoutPublishingOrReusingEvidence(String fault) throws Exception {
		var inputs=RelatedFixture.compile(dir);Path path=dir.resolve("project.jadx"),mapping=dir.resolve("aliases.tiny");
		Files.writeString(mapping,"tiny\t2\t0\toriginal\tmapped\nc\trelated/UnrelatedCold\trelated/MappedCold\n");NativeProjectDocument.newFromInputs(path,inputs).withMappingsPath(mapping).save();
		try(var runtime=RelatedGroupAdmissionTest.open(path)) {
			var evidence=runtime.verifyRelatedHierarchy(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults());assertEquals(4,evidence.members().size());
			Path changed=fault.startsWith("input")?inputs.getFirst():mapping;byte[] original=Files.readAllBytes(changed);
			var before=runtime.projectSnapshot();var identity=runtime.searchIdentity();var pending=runtime.pendingEdits();var old=runtime.decompiler();
			Runnable change=()->{try{Files.write(changed,new byte[]{'\n'},StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}};
			if(fault.endsWith("before"))change.run();else runtime.replacementHook(s->{if(s==ProjectRuntime.ReplacementStage.LOADED)change.run();});
			var failure=assertThrows(EditBatchService.Rejected.class,()->new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS))));
			assertEquals(409,failure.status());assertEquals("EXTERNAL_MODIFICATION_CONFLICT",failure.code());assertEquals(0,failure.itemErrors().getFirst().index());
			assertEquals(before,runtime.projectSnapshot());assertEquals(identity,runtime.searchIdentity());assertEquals(pending,runtime.pendingEdits());assertSame(old,runtime.decompiler());assertEquals("READY",runtime.status().state());
			runtime.replacementHook(s->{});
			RelatedGroupAdmissionTest.rejects(runtime,409,"EXTERNAL_MODIFICATION_CONFLICT",RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS));
			Files.write(changed,original);var r=runtime.projectSnapshot().revisions();runtime.reloadProject(true,r.sessionId(),r.logicalRevision());
			assertEquals("APPLIED",new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS))).outcome());
		}
	}

	@Test void invalidFamiliesFailClosedAndDuplicateNativeRecordsReject() throws Exception {
		var inputs=new ArrayList<>(RelatedFixture.compile(dir));inputs.add(RelatedFixture.duplicateLeaf(dir));
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			RelatedGroupAdmissionTest.rejects(runtime,422,"INVALID_ENTITY_ID",RelatedGroupAdmissionTest.propagated(RelatedFixture.ref("Base","work","(I)I"),"Alias"));
		}
		inputs.removeLast();
		Path path=dir.resolve("ambiguous.jadx");var doc=NativeProjectDocument.newFromInputs(path,inputs);
		var key=new jadx.api.data.impl.JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,"related.Hierarchy$SeparateRight","joined(I)I");
		var code=new jadx.api.data.impl.JadxCodeData();code.setRenames(List.of(new jadx.api.data.impl.JadxCodeRename(key,"one"),new jadx.api.data.impl.JadxCodeRename(key,"two")));doc.setCodeData(code);doc.save();
		try(var runtime=RelatedGroupAdmissionTest.open(path)) { RelatedGroupAdmissionTest.rejects(runtime,422,"INVALID_ENTITY_ID",RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,"Alias")); }
	}

	@Test void parameterRenameCannotOverlapGroupAndTwoDisjointGroupsPublishOnce() throws Exception {
		var inputs=RelatedFixture.compile(dir);
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			var ref=RelatedGroupAdmissionTest.SEED;
			var source=runtime.withExclusiveEdit(c->dev.libjadx.jadxadapter.JadxSourceAdapter.extract(c.decompiler(),
					dev.libjadx.jadxadapter.JadxSymbolAdapter.visibleClass(c.decompiler(),ref.originalClassDescriptor(),0),SymbolRef.classRef(ref.originalClassDescriptor()),false,
					c.before().revisions().sessionId(),c.before().revisions().logicalRevision(),c.publicationEpoch(),c.settings().fingerprint()));
			var parameter=new EditDtos.Operation(EditDtos.Kind.RENAME_PARAMETER,ref,"count",null,null,0,source.sourceSnapshotId());
			var group=RelatedGroupAdmissionTest.propagated(ref,RelatedGroupAdmissionTest.ALIAS);
			RelatedGroupAdmissionTest.rejects(runtime,400,"INVALID_REQUEST",group,parameter);RelatedGroupAdmissionTest.rejects(runtime,400,"INVALID_REQUEST",parameter,group);
			var identity=runtime.searchIdentity();var count=new java.util.concurrent.atomic.AtomicInteger();runtime.replacementHook(s->{if(s==ProjectRuntime.ReplacementStage.BEFORE_CONSTRUCTION)count.incrementAndGet();});
			var second=RelatedGroupAdmissionTest.propagated(RelatedFixture.ref("Base","work","(I)I"),"renamedWork");
			var result=new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,group,second));
			assertEquals(1,count.get());assertEquals(1,result.logicalRevisionAfter());assertEquals(identity.publicationEpoch()+1,runtime.searchIdentity().publicationEpoch());
			assertEquals(4,result.items().get(0).affectedRefs().size());assertEquals(5,result.items().get(1).affectedRefs().size());
		}
	}
}
