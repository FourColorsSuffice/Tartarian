package project.tartarian;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DependecyGraphCreator {

    public static void prepareDatabase(String host, String user, String password, String urlToMaven) {

        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection(
                    "jdbc:neo4j:bolt://" + host + "/?user=" + user + ",password=" + password + ",noSsl");


            //1) retrieve affected branch
            String delete = "MATCH (n) DETACH DELETE n";
            PreparedStatement preparedStatement = con.prepareStatement(delete);
            ResultSet rs = preparedStatement.executeQuery();

            //2) create data
            String create =
                    "CREATE(jetty:Project{name:\"Jetty\",location:\"https://github.com/eclipse/jetty.project\"})\n" +
                            "CREATE(rb94:Release{name:\"9.4.x\"})\n" +
                            "CREATE(rb93:Release{name:\"9.3.x\"})\n" +
                            "CREATE(rb92:Release{name:\"9.2.x\"})\n" +
                            "CREATE(jetty)-[b94:Branch]->(rb94)\n" +
                            "CREATE(jetty)-[b93:Branch]->(rb93)\n" +
                            "CREATE(jetty)-[b92:Branch]->(rb92)\n" +
                            "CREATE(jdk:Library{name:\"JDK\",location:\"https://www.oracle.com/java/\"})\n" +
                            "CREATE(v17:Version{name:1.7})\n" +
                            "CREATE(v18:Version{name:1.8})\n" +
                            "CREATE(jdk)-[l17:Branch]->(v17)\n" +
                            "CREATE(jdk)-[l18:Branch]->(v18)\n" +
                            "CREATE(rb94)-[drb94v18:Depends]->(v18)\n" +
                            "CREATE(rb93)-[drb93v18:Depends]->(v18)\n" +
                            "CREATE(rb92)-[drb92v17:Depends]->(v17)";

            preparedStatement = con.prepareStatement(create);
            rs = preparedStatement.executeQuery();

            con.close();

            System.out.println("Connected OK.");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
