package project.tartarian;

import java.sql.*;
import java.util.*;

public class BranchIdentifier {

    //private static String host = "localhost";
    //private static String password = "neo4j";

    static String host = "localhost";
    static String user = "neo4j";
    static String pass = "tartarian";
    static String urlToMaven;
    static String commitId;
    static String commitMessage;
    static String commitTo;
    static String currentBranch;

    public static void main(String[] args) {

        String para = args[0];
        if (para.equals("-dMaven")) {
            urlToMaven = args[1];
            DependecyGraphCreator.prepareDatabase(host, user, pass, urlToMaven);
        } else if (para.equals("-c")) {
            commitId = args[1];
            commitMessage = args[2];
            commitTo = args[3];
            identifyBranch(commitId, commitMessage, commitTo);
        } else if (para.equals("-r")) {
            currentBranch = args[1];
            retrieveBranch(currentBranch);
        }

    }

    public static void retrieveBranch(String currentBranch){
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection(
                    "jdbc:neo4j:bolt://" + host + "/?user=" + user + ",password=" + pass + ",noSsl");
            //1)make a query
            PreparedStatement p = getCyperToRetrieveSuggestions(con, currentBranch);
            ResultSet rs = p.executeQuery();
            //report what is suggested
            List<String> list = new ArrayList();
            System.out.println("These are the commits applicable to branch " + currentBranch);
            while (rs.next()) {
                String commitId = rs.getString("c.commitid" );
                String commitMessage = rs.getString("c.message" );
                System.out.println("Tartarian suggested cherry-picking commit #" + commitId + " with message " + commitMessage);
                Scanner input = new Scanner(System.in);
                System.out.println("For commit #" + commitId + ", press y to cherry pick, n to not cherry pick, anything else to ignore:");
                String a = input.next();
                if(a.equalsIgnoreCase("y")){
                    //do cherrypick in Git
                    String cherrPickId = commitId + "0000"; //change to output from Git later
                    p = getCyperToUpdateCherryPick(con, true, commitId, cherrPickId);
                    rs = p.executeQuery();
                }else if(a.equalsIgnoreCase("n")){
                    //change status of suggestion to "NotApplicable"
                    p = getCyperToUpdateCherryPick(con, false, commitId, null);
                    rs = p.executeQuery();
                }
            }


            con.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public static void identifyBranch(String commitId, String commitMessage, String branchName) {


        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection(
                    "jdbc:neo4j:bolt://" + host + "/?user=" + user + ",password=" + pass + ",noSsl");

            //1)create a new commit and link to existing branch
            PreparedStatement[] ps = getCyperToCreateNewCommit(con, commitId, commitMessage, branchName);
            ps[0].executeQuery();
            ps[1].executeQuery();

            //2)make a query
            PreparedStatement p = translateTagToCypher(con, commitMessage);
            ResultSet rs = p.executeQuery();

            //report what is suggested
            List<String> list = new ArrayList();
            System.out.println("Matched");
            while (rs.next()) {
                System.out.println(rs.getString("r.name"));
                list.add(rs.getString("r.name"));
            }
            List<String> branchesToCreate = new ArrayList();
            String[] branches = list.toArray(new String[list.size()]);
            System.out.println("Created");
            for (String b : branches) {
                if (!b.equals(branchName)) {
                    System.out.println(b);
                    branchesToCreate.add(b);
                }
            }
            String[] branchNamesSuggested = branchesToCreate.toArray(new String[branchesToCreate.size()]);

            //3)create suggestion
            PreparedStatement[][] pss = getCyperToCreateSuggestions(con, commitId, commitMessage, branchName, branchNamesSuggested);
            for (PreparedStatement[] q : pss) {
                q[0].executeQuery();
                q[1].executeQuery();
            }

            con.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static PreparedStatement[] getCyperToCreateNewCommit(Connection con,
                                                                 String commitId, String commitMessage,
                                                                 String branchId)
            throws SQLException {

        PreparedStatement[] ps = new PreparedStatement[2];
        //create and link to existing branch
        String cypher = "CREATE (c:Commit {commitid:?, message:?, status:\"committed\"})";

        ps[0] = con.prepareStatement(cypher);
        ps[0].setString(1, commitId);
        ps[0].setString(2, commitMessage);

        String cypher2 = "MATCH (c:Commit {commitid:?})\n" +
                "MATCH (r:Release {name:? })\n" +
                "CREATE (c)-[committed:Committed]->(r)";

        ps[1] = con.prepareStatement(cypher2);
        ps[1].setString(1, commitId);
        ps[1].setString(2, branchId);

        return ps;

    }

    private static PreparedStatement translateTagToCypher(Connection con, String commitMessage)
            throws SQLException {

        CommitMessageAnalyzer cma = new CommitMessageAnalyzer(commitMessage);

        String cypher = "MATCH (r:Release)-[:Depends]->(v:Version)<-[:Branch]-(l:Library)\n" +
                "WHERE l.name = ? AND toFloat(v.name) >= ? " +
                "RETURN r.name";

        PreparedStatement p = con.prepareStatement(cypher);
        p.setString(1, cma.dependency);
        p.setDouble(2, Double.parseDouble(cma.version));
        return p;

    }

    private static PreparedStatement[][] getCyperToCreateSuggestions(Connection con,
                                                                     String commitId, String commitMessage,
                                                                     String branchIdOrigin, String[] branchIdsSuggested)
            throws SQLException {

        //return two-dimensioned array [i][0] for create [i][1] for link
        PreparedStatement[][] pss = new PreparedStatement[branchIdsSuggested.length][];

        String create = "CREATE (c:Commit {commitid:?, message:?, status:\"suggested\"})";
        String link = "MATCH (c:Commit {commitid:?})\n" +
                "MATCH (r:Release {name:? })\n" +
                "CREATE (c)-[applicable:Applicable]->(r)";

        //TODO must create Cypher to create Suggesting with status = open
        //create and link to existing branch
        int i = 0;
        for (String branchIdSuggested : branchIdsSuggested) {

            PreparedStatement[] ps = new PreparedStatement[2];

            PreparedStatement pCreate = con.prepareStatement(create);
            pCreate.setString(1, commitId + "Into" + branchIdSuggested);
            pCreate.setString(2, "cherry picked from " + commitId);
            ps[0] = pCreate;

            PreparedStatement pLink = con.prepareStatement(link);
            pLink.setString(1, commitId + "Into" + branchIdSuggested);
            pLink.setString(2, branchIdSuggested);
            ps[1] = pLink;

            pss[i] = ps;
            i++;
        }

        return pss;

    }

    private static PreparedStatement getCyperToRetrieveSuggestions(Connection con,
                                                                     String currentBranch)
            throws SQLException {

        String find = "MATCH (c:Commit)-[applicable:Applicable]->(r:Release)\n" +
                "WHERE c.status = ? AND r.name = ? " +
                "RETURN c.commitid, c.message";
        PreparedStatement ps = con.prepareStatement(find);
        ps.setString(1, "suggested");
        ps.setString(2, currentBranch);

        return ps;

    }

    private static PreparedStatement getCyperToUpdateCherryPick(Connection con, boolean isCherryPicked, String commidId, String cherryPickId)
            throws Exception {

        String update;
        PreparedStatement ps;

        if(isCherryPicked==false){
            //update to not applicable
            update = "MATCH (c:Commit)-[applicable:Applicable]->(r:Release)\n" +
                    "WHERE c.commitid = ? AND c.status = \"suggested\"\n" +
                    "SET c.status = \"not applicable\"";
            ps = con.prepareStatement(update);
            ps.setString(1, commidId);

        }else{
            //update to actual cherry pick
            update = "MATCH (c:Commit)-[applicable:Applicable]->(r:Release)\n" +
                    "WHERE c.commitid = ? AND c.status = \"suggested\"\n" +
                    "SET c.commitid = ? \n"+
                    "SET c.status = \"committed\""+
                    "CREATE (c)-[committed:Committed]->(r)\n"+
                    "SET committed = applicable\n"+
                    "WITH applicable\n"+
                    "DELETE applicable";
            ps = con.prepareStatement(update);
            ps.setString(1, commidId);
            ps.setString(2, cherryPickId);
        }

        return ps;

    }

}
