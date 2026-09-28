package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.*;
import jadx.api.data.impl.*;
import jadx.api.metadata.annotations.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JadxVariableProbeTest {
	@TempDir Path dir;

	static JadxDecompiler open(Path jar, JadxCodeData data, DecompilationMode mode) {
		JadxArgs args = new JadxArgs();
		args.setInputFiles(List.of(jar.toFile())); args.setCodeData(data); args.setDecompilationMode(mode);
		JadxDecompiler engine = new JadxDecompiler(args); engine.load(); engine.getClasses().forEach(cls -> cls.getClassNode().add(jadx.core.dex.attributes.AFlag.DONT_UNLOAD_CLASS)); return engine;
	}

	static Map<String, VarNode> variables(JavaClass cls) {
		Map<String, VarNode> result = new TreeMap<>();
		for (var entry : cls.getCodeInfo().getCodeMetadata().getAsMap().entrySet()) {
			if (entry.getValue() instanceof NodeDeclareRef declaration && declaration.getNode() instanceof VarNode var) {
				result.put(var.getMth().getMethodInfo().getShortId() + ":" + var.getReg() + ":" + var.getSsa(), var);
			}
		}
		return result;
	}

	@Test void positionalArgumentsExcludeThisAndWideSlotsAndSurviveNativeReopen() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		JadxCodeData data = new JadxCodeData();
		List<jadx.api.data.ICodeRename> renames = new ArrayList<>();
		try (var engine = open(jar, data, DecompilationMode.RESTRUCTURE)) {
			JavaClass cls = engine.searchJavaClassByOrigFullName("probe.Variables"); cls.getCodeInfo();
			for (JavaMethod method : cls.getMethods()) {
				if (method.isConstructor()) continue;
				var node = method.getMethodNode();
				assertEquals(node.getMethodInfo().getArgumentsTypes().size(), node.getArgRegs().size());
				for (int i = 0; i < node.getArgRegs().size(); i++) {
					assertFalse(node.getArgRegs().get(i).isThis());
					renames.add(new JadxCodeRename(JadxNodeRef.forMth(method), JadxCodeRef.forMthArg(i), "parameter" + i));
				}
			}
		}
		data.setRenames(renames);
		Path project = dir.resolve("variables.jadx");
		var doc = NativeProjectDocument.newFromInputs(project, List.of(jar)); doc.setCodeData(data); doc.save();
		try (var engine = open(jar, NativeProjectDocument.open(project).getCodeData(), DecompilationMode.RESTRUCTURE)) {
			JavaClass cls = engine.searchJavaClassByOrigFullName("probe.Variables");
			String source = cls.getCode();
			assertTrue(source.contains("instance(int parameter0, long parameter1, double parameter2, String parameter3)"), source);
			assertTrue(source.contains("statik(long parameter0, int parameter1, double parameter2)"), source);
			assertTrue(source.contains("single(int parameter0)"), source);
			assertTrue(source.contains("single(String parameter0)"), source);
			var before = variables(cls).keySet();
			cls.unload(); engine.reloadCodeData();
			assertEquals(before, variables(cls).keySet());
			assertTrue(cls.getCode().contains("long parameter1"));
		}
	}

	@Test void localNativeKeysAcrossReplayReopenAndModes() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		Map<String, String> baseline = new TreeMap<>();
		JadxCodeData data = new JadxCodeData();
		try (var engine = open(jar, data, DecompilationMode.RESTRUCTURE)) {
			JavaClass cls = engine.searchJavaClassByOrigFullName("probe.Variables");
			Map<String, VarNode> vars = variables(cls);
			List<jadx.api.data.ICodeRename> renames = new ArrayList<>();
			for (var entry : vars.entrySet()) {
				VarNode var = entry.getValue();
				if (var.getMth().getArgRegs().stream().anyMatch(arg -> arg.getRegNum() == var.getReg() && arg.getSVar().getVersion() == var.getSsa())) continue;
				String name = "local" + renames.size(); baseline.put(entry.getKey(), name);
				renames.add(new JadxCodeRename(JadxNodeRef.forMth((JavaMethod) engine.getJavaNodeByRef(var.getMth())), JadxCodeRef.forVar(var), name));
			}
			assertFalse(renames.isEmpty()); data.setRenames(renames);
		}
		Path project = dir.resolve("local-probe.jadx");
		var document = NativeProjectDocument.newFromInputs(project, List.of(jar));
		document.setCodeData(data); document.save();
		data = NativeProjectDocument.open(project).getCodeData();
		for (DecompilationMode mode : List.of(DecompilationMode.RESTRUCTURE, DecompilationMode.SIMPLE, DecompilationMode.FALLBACK)) {
			try (var engine = open(jar, data, mode)) {
				var cls = engine.searchJavaClassByOrigFullName("probe.Variables");
				Map<String, String> names = new TreeMap<>(); variables(cls).forEach((key, var) -> names.put(key, var.getName()));
				System.out.println("MODE " + mode + " baseline=" + baseline + " emitted=" + names);
				if (mode == DecompilationMode.RESTRUCTURE) {
					baseline.forEach((key, name) -> assertEquals(name, names.get(key), key));
					cls.unload(); engine.reloadCodeData();
					var repeated = variables(cls);
					baseline.forEach((key, name) -> assertEquals(name, repeated.get(key).getName(), key));
					var replay = NativeProjectDocument.copyCodeData(data);
					var renames = new ArrayList<>(replay.getRenames());
					renames.add(new JadxCodeRename(JadxNodeRef.forCls("probe.VariableUnrelated"), "RenamedUnrelated"));
					replay.setRenames(renames); engine.getArgs().setCodeData(replay); cls.unload(); engine.reloadCodeData();
					var afterDeclaration = variables(cls);
					baseline.forEach((key, name) -> assertEquals(name, afterDeclaration.get(key).getName(), key));
				}
				if (mode == DecompilationMode.SIMPLE) {
					assertFalse(names.containsKey("merged(I)I:5:1"));
					assertTrue(names.containsKey("merged(I)I:5:0"));
				}
				if (mode == DecompilationMode.FALLBACK) assertTrue(names.isEmpty());
			}
		}
	}
}
