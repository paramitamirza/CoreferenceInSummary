package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.json.*;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;

public class WikiaCharacters {
	
	public String listOfCharacters(String wikiaUrl, String url, Gender gender) {
		URL oracle = new URL("http://harrypotter.wikia.com/api/v1/Articles/List?expand=1&category=Females&limit=2000");
        BufferedReader in = new BufferedReader(
        new InputStreamReader(oracle.openStream()));

        String input = "", inputLine;
        while ((inputLine = in.readLine()) != null)
            input += inputLine + "\n";
        in.close();
		
		JSONObject obj = new JSONObject(input);
		JSONArray arr = obj.getJSONArray("items");
		for (int i = 0; i < arr.length(); i++) {
			JSONObject character = (JSONObject)arr.get(i);
			
		    System.out.println(""character.get("title") + );
		}
		
		return "";
	}
	
	public static void main(String[] args) throws IOException {
		
		

		
	}

}
