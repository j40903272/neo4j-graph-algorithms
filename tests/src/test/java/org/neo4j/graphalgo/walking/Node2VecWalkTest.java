package org.neo4j.graphalgo.walking;

import org.junit.*;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class Node2VecWalkTest {

    private static final int NODE_COUNT = 54;

    private static GraphDatabaseAPI db;
    private Transaction tx;

    @BeforeClass
    public static void beforeClass() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase();
        db.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(NodeWalkerProc.class);

        db.execute(buildDatabaseQuery(), Collections.singletonMap("count",NODE_COUNT-4)).close();
    }

    @AfterClass
    public static void AfterClass() {
        db.shutdown();
    }

    @Before
    public void setUp() throws Exception {
        tx = db.beginTx();
    }

    @After
    public void tearDown() throws Exception {
        tx.close();
    }

    private static String buildDatabaseQuery() {
        return "CREATE (a:Node {name:'a'})\n" +
                "CREATE (b:Fred {name:'b'})\n" +
                "CREATE (c:Fred {name:'c'})\n" +
                "CREATE (d:Bob {name:'d'})\n" +

                "CREATE" +
                " (a)-[:OF_TYPE {cost:5, blue: 1}]->(b),\n" +
                " (a)-[:OF_TYPE {cost:10, blue: 1}]->(c),\n" +
                " (c)-[:DIFFERENT {cost:2, blue: 0}]->(b),\n" +
                " (b)-[:OF_TYPE {cost:5, blue: 1}]->(c) " +

                " WITH * UNWIND range(0,$count-1) AS id CREATE (n:Node {name:''+id})\n" +
                "CREATE (n)-[:OF_TYPE {cost:5, blue: 1}]->(a),\n" +
                "(b)-[:OF_TYPE {cost:5, blue: 1}]->(n)\n";
    }

    @Test
    public void shouldHaveGivenStartNodeRandom() {
        try (ResourceIterator<List<Long>> result = db.execute("CALL algo.randomWalk.stream(null,null, 1, 1, 1)").columnAs("nodes")) {

            List<Long> path = result.next();
            assertEquals(1L, path.get(0).longValue());
            assertNotEquals(1L, path.get(1).longValue());
        }
    }

    @Test(timeout = 100)
    public void shouldHaveResultsRandom() {
        Result results = db.execute("CALL algo.randomWalk.stream(null,null, null, 1, 5)");

        assertTrue(results.hasNext());
    }

    @Test
    public void shouldHandleLargeResults() {
        Result results = db.execute("CALL algo.randomWalk.stream(null,null, null, 100, 100000)");

        assertEquals(100000,Iterators.count(results));
    }

    @Test
    public void shouldHaveSameTypesForStartNodesRandom() {
        // TODO: make this test predictable (i.e. set random seed)
        ResourceIterator<Path> results = db.execute("CALL algo.randomWalk.stream(null,null, 'Fred', 2, 5,{path:true})").columnAs("path");
        int count = 0;
        while (results.hasNext()) {
            Path path = results.next();
            assertTrue("Nodes should be of type 'Fred'.", path.startNode().hasLabel(Label.label("Fred")));
            assertEquals(1, path.length());
            Relationship firstRel = path.relationships().iterator().next();
            assertEquals(path.startNode(), firstRel.getStartNode());
            count ++;
        }
        assertEquals(5, count);
    }

    @Test(timeout = 200)
    public void shouldHaveStartedFromEveryNodeRandom() {
        ResourceIterator<List<Long>> results = db.execute("CALL algo.randomWalk.stream(null,null,null,1,100)").columnAs("nodes");

        Set<Long> nodeIds = new HashSet<>();
        while (results.hasNext()) {
            List<Long> path = results.next();
            nodeIds.add(path.get(0));
        }
        assertEquals("Should have visited all nodes.", NODE_COUNT, nodeIds.size());
    }

    @Test
    public void shouldNotFailRandom() {
        Result results = db.execute("CALL algo.randomWalk.stream(null,null, 2, 7, 2)");

        results.next();
        results.next();
        assertTrue("There should be only two results.", !results.hasNext());
    }

    private Node getStartNode(Path path) {
        return path.startNode();
    }

    private long getStartNodeId(Path path) {
        return path.startNode().getId();
    }

    @Test
    public void shouldHaveGivenStartNode() {
        List<Long> result = db.execute("CALL algo.randomWalk.stream(null, null, 1, 1, 1, {mode:'node2vec', return: 1, inOut:1})").<List<Long>>columnAs("nodes").next();

        assertThat( 1L, equalTo(result.get(0)));
    }

    @Test(timeout = 100)
    public void shouldHaveResultsN2V() {
        ResourceIterator<List<Long>> results = db.execute("CALL algo.randomWalk.stream(null, null, null, 1, 5, {mode:'node2vec', return: 1, inOut:1})").columnAs("nodes");

        assertTrue(results.hasNext());
    }

    @Test
    public void shouldHaveStartedFromEveryNodeN2V() {
        ResourceIterator<List<Long>> results = db.execute("CALL algo.randomWalk.stream(null, null, null, 1, 100, {mode:'node2vec', return: 1, inOut:1})").columnAs("nodes");

        Set<Long> nodeIds = new HashSet<>();
        while (results.hasNext()) {
            List<Long> record = results.next();
            nodeIds.add(record.get(0));
        }
        assertEquals("Should have visited all nodes.",  NODE_COUNT, nodeIds.size());
    }

    @Test
    public void shouldNotFailN2V() {
        ResourceIterator<List<Long>> results = db.execute("CALL algo.randomWalk.stream(null, null, 2, 7, 2, {mode:'node2vec', return: 1, inOut:1})").columnAs("nodes");

        results.next();
        results.next();
        assertTrue("There should be only two results.", !results.hasNext());
    }
}
