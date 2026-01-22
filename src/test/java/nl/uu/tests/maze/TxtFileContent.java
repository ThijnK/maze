package nl.uu.tests.maze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * A utility class representing the content of a text file.
 */
public class TxtFileContent {
	
	public String content ;
	public List<String> lines ;
	
	public TxtFileContent(String path) throws IOException {
		content = Files.readString(Paths.get(path)) ;
		lines = Arrays.asList(content.split("\n")) ;
	}
	
	public TxtFileContent(Path path) throws IOException {
		content = Files.readString(path) ;
		lines = Arrays.asList(content.split("\n")) ;
	}
	
	public int countMatchingLines(Predicate<String> P) {
		
		return (int) lines.stream().filter(P).count() ;
	}
	
	public boolean matchAnyLine(Predicate<String> P) {
		
		return countMatchingLines(P) > 0 ;
	}
	
	
}
