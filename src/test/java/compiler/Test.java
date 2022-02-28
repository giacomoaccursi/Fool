package compiler;

import java.io.IOException;

public class Test {

    private final FOOLTester foolTester = new FOOLTester();
    private final String resourceDir = "src/test/resources/";

    @org.junit.jupiter.api.Test
    public void testObjectOrientation() throws IOException {
        foolTester.runProgram(resourceDir + "quicksort.fool");
    }

    @org.junit.jupiter.api.Test
    public void testInheritance() throws IOException {
        foolTester.runProgram(resourceDir + "bankloan.fool");
    }
}
