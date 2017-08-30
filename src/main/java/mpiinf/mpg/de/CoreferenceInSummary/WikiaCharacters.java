package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.*;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;

public class WikiaCharacters {
	
	public String listOfCharacters(String wikiaUrl, String url, Gender gender) throws IOException {
        BufferedReader in = new BufferedReader(
        new InputStreamReader(new URL(url).openStream()));

        String input = "", inputLine;
        while ((inputLine = in.readLine()) != null)
            input += inputLine + "\n";
        in.close();
        
        StringBuilder output = new StringBuilder();
        
        String gen = "N";
        if (gender == Gender.FEMALE) gen = "F";
        else if (gender == Gender.MALE) gen = "M";
		
		JSONObject obj = new JSONObject(input);
		JSONArray arr = obj.getJSONArray("items");
		for (int i = 0; i < arr.length(); i++) {
			JSONObject character = (JSONObject)arr.get(i);
			
			String title = character.getString("title");
			String wikiaPage = wikiaUrl + character.getString("url");
			String wikiaAbstract = character.getString("abstract");
			String image = character.get("thumbnail").toString();
//			if (!title.contains("'s")) {
				output.append(gen + ",\"" + title + "\"," + wikiaPage + "\",\"" + wikiaAbstract + "\",\"" + image + "\"\n");
//			}
		}
		
		return output.toString();
	}
	
	public static void main(String[] args) throws IOException {
		
		String femaleUrl = "http://harrypotter.wikia.com/api/v1/Articles/List?expand=1&category=Females&limit=2000";
		String maleUrl = "http://harrypotter.wikia.com/api/v1/Articles/List?expand=1&category=Males&limit=2000";
		
		WikiaCharacters wc = new WikiaCharacters();
		
		BufferedWriter bw = new BufferedWriter(new FileWriter("data/wikia.harry.potter.female.character.csv"));
		bw.write(wc.listOfCharacters("http://harrypotter.wikia.com", femaleUrl, Gender.FEMALE));
		bw.close();
		
		bw = new BufferedWriter(new FileWriter("data/wikia.harry.potter.male.character.csv"));
		bw.write(wc.listOfCharacters("http://harrypotter.wikia.com", maleUrl, Gender.MALE));
		bw.close();

		
	}

}
