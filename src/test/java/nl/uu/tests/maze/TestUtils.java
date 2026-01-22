package nl.uu.tests.maze;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Providing bunch of utilities for test functions.
 */
public class TestUtils {
	
	/**
	 * Delete a file, swallowing exception if the file does not exist.
	 */
	public static void removeFile(String path) {		
		try {
			Files.delete(Paths.get(path)) ;
		}
		catch(Exception e) { }
	}
	
	/**
	 * Delete a file, swallowing exception if the file does not exist.
	 */
	public static void removeFile(Path path) {		
		try {
			Files.delete(path) ;
		}
		catch(Exception e) { }
	}

}
