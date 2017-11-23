package mpiinf.mpg.de.CoreferenceInSummary;

public class Fact {
	
	private String subject;
	private String predicate;
	private String object;
	
	public Fact(String subj, String pred, String obj) {
		subject = subj;
		predicate = pred;
		object = obj;
	}
	
	public String toString() {
		return "<" + subject + "," + predicate + "," + object + ">";
	}
	
	public static Fact loadFromString(String factStr) {
		String[] factString = factStr.substring(1, factStr.length()-1).split(",");
		Fact f = new Fact(factString[0], factString[1], factString[2]);
		return f;
	}

}
