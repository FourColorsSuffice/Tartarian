package project.tartarian;

public class CommitMessageAnalyzer {

    String commitMessage;
    String tag;
    String dependency;
    String version;

    public CommitMessageAnalyzer(String commitMessage){
        this.commitMessage = "fix bug";
        this.tag = "#bugfix";
        this.dependency = "JDK";
        this.version = "1.8";
    }
}
