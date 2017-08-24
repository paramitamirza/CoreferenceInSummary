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

}
