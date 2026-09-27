package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import dev.libjadx.core.references.*;
import dev.libjadx.core.symbols.*;
import dev.libjadx.core.source.SourceCoordinates;
import org.junit.jupiter.api.Test;

class ReferenceSnapshotTest {
    final byte[] key = SymbolCatalog.newCursorKey();
    final SymbolRef ref = ReferenceEndpointsTest.method("entry","()V");
    ReferenceEdge edge(String name) {
        return new ReferenceEdge(ref,ReferenceEndpointsTest.method(name,"()V"),ReferenceQuery.Relation.CALL,
                ReferenceEdge.Resolution.RESOLVED,ReferenceEdge.Evidence.JADX_METHOD_USED,List.of(),"UNAVAILABLE",null);
    }
    ReferenceQuery query() { return new ReferenceQuery(ref,ReferenceQuery.Direction.OUTGOING,null,1,null,false,false,null,null); }
    ReferenceSnapshot snapshot(List<ReferenceEdge> edges,String session,long revision,long epoch,String settings) {
        return new ReferenceSnapshot(edges,query(),session,revision,epoch,settings,key);
    }
    @Test void graphChangeAtSameRevisionStalesAndAuthenticationPrecedesClassification() {
        var edges=List.of(edge("b"),edge("a"));
        var first=snapshot(edges,"one",0,0,"settings");
        var reordered=snapshot(List.of(edge("a"),edge("b"),edge("a")),"one",0,0,"settings");
        assertEquals(first.id(),reordered.id());
        var page=first.page(query(),null);
        assertEquals("a",page.edges().getFirst().targetRef().originalName());
        var cursor=ReferenceSnapshot.authenticate(page.nextCursor(),key);
        assertEquals("b",reordered.page(query(),cursor).edges().getFirst().targetRef().originalName());
        for(var changed:List.of(snapshot(List.of(edge("a"),edge("b"),edge("c")),"one",0,0,"settings"),
                snapshot(edges,"two",0,0,"settings"),snapshot(edges,"one",1,0,"settings"),
                snapshot(edges,"one",0,1,"settings"),snapshot(edges,"one",0,0,"different")))
            assertThrows(ReferenceSnapshot.StaleReferenceException.class,()->changed.page(query(),cursor));
        var parts=page.nextCursor().split("\\."); byte[] bytes=Base64.getUrlDecoder().decode(parts[0]); bytes[80]^=1;
        String tampered=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+parts[1];
        assertThrows(IllegalArgumentException.class,()->ReferenceSnapshot.authenticate(tampered,key));
        assertThrows(IllegalArgumentException.class,()->ReferenceSnapshot.authenticate(page.nextCursor(),SymbolCatalog.newCursorKey()));
    }
    @Test void limitsRejectAtCollectionAndDtosAreImmutable() {
        var budget=new ReferenceSnapshot.Budget();
        for(int i=0;i<10000;i++) budget.edge(SymbolRef.classRef("La;"),SymbolRef.classRef("Lb;"));
        assertThrows(ReferenceSnapshot.ReferenceLimitException.class,()->budget.edge(SymbolRef.classRef("La;"),SymbolRef.classRef("Lb;")));
        var sites=new ReferenceSnapshot.Budget();
        var site=new ReferenceEdge.Site(SymbolRef.classRef("La;"),"s",new SourceCoordinates.Point(0,1,0),"EXACT");
        // The conservative string budget can reject before the site ceiling.
        assertThrows(ReferenceSnapshot.ReferenceLimitException.class,()->{for(int i=0;i<20001;i++) sites.site(site);});
        var strings=new ReferenceSnapshot.Budget();
        var large=new SymbolRef(SymbolRef.Kind.METHOD,"L"+"a".repeat(1000)+";",null,"b".repeat(500),"()V");
        assertThrows(ReferenceSnapshot.ReferenceLimitException.class,()->{for(int i=0;i<1000;i++) strings.edge(large,large);});
        assertThrows(UnsupportedOperationException.class,()->snapshot(List.of(edge("a")),"one",0,0,"s").edges().clear());
    }

    @Test void sourceSiteChangeAtSameRevisionInvalidatesCompactCursor() {
        var site = new ReferenceEdge.Site(SymbolRef.classRef("Lprobe/ReferenceFixture;"),
                "sha256:" + "a".repeat(64), new SourceCoordinates.Point(20, 2, 4), "EXACT");
        var original = edge("a");
        var withSite = new ReferenceEdge(original.sourceRef(), original.targetRef(), original.relation(),
                original.resolution(), original.evidence(), List.of(site), "PARTIAL", null);
        var query = new ReferenceQuery(ref, ReferenceQuery.Direction.OUTGOING, null, 1,
                null, true, false, null, null);
        var before = new ReferenceSnapshot(List.of(withSite, edge("b")), query, "one", 0, 0, "settings", key);
        var cursor = ReferenceSnapshot.authenticate(before.page(query, null).nextCursor(), key);
        var changed = new ReferenceSnapshot(List.of(original, edge("b")), query, "one", 0, 0, "settings", key);
        assertNotEquals(before.id(), changed.id());
        assertThrows(ReferenceSnapshot.StaleReferenceException.class, () -> changed.page(query, cursor));
    }
}
