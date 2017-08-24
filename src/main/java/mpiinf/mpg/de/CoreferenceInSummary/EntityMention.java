package mpiinf.mpg.de.CoreferenceInSummary;

public class EntityMention {
	
	private Integer startIdx;
	private Integer endIdx;
	
	public EntityMention(int start, int end) {
		setStartIdx(start);
		setEndIdx(end);
	}

	public Integer getStartIdx() {
		return startIdx;
	}

	public void setStartIdx(Integer startIdx) {
		this.startIdx = startIdx;
	}

	public Integer getEndIdx() {
		return endIdx;
	}

	public void setEndIdx(Integer endIdx) {
		this.endIdx = endIdx;
	}
	
	public String toString() {
		String mentions = startIdx + "-" + endIdx;
		return mentions;
	}

}
