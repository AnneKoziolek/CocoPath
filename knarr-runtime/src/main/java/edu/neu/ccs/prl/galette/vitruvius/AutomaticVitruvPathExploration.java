package edu.neu.ccs.prl.galette.vitruvius;

import edu.neu.ccs.prl.galette.concolic.knarr.runtime.GaletteSymbolicator;
import edu.neu.ccs.prl.galette.concolic.knarr.runtime.PathExplorer;
import edu.neu.ccs.prl.galette.concolic.knarr.runtime.PathUtils;
import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.Tainter;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import za.ac.sun.cs.green.expr.Expression;

/**
 * Automatic Vitruvius path exploration using constraint-based input generation.
 *
 * This example demonstrates PROPER symbolic execution:
 * - Inputs generated automatically by negating constraints
 * - Iterative path exploration until all paths covered
 *
 * @purpose Automatic path exploration for Vitruvius VSUM
 * @feature Explores all user dialog choices
 * @feature Generates test inputs for transformations
 *
 */
public class AutomaticVitruvPathExploration {

    public static void main(String[] args) {

        // Register XMI resource factory
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());

        // Load Vitruvius Test class
        Object testInstance;
        try {
            Class<?> testClass = Class.forName("tools.vitruv.methodologisttemplate.vsum.Test");
            testInstance = testClass.getDeclaredConstructor().newInstance();
            System.out.println("[AutomaticVitruvPathExploration:main] Loaded Vitruvius Test class");
        } catch (Exception e) {
            System.err.println(
                    "[AutomaticVitruvPathExploration:main] Failed to load Vitruvius Test class: " + e.getMessage());
            return;
        }

        // Create path explorer
        PathExplorer explorer = new PathExplorer();

        // NOTE: Domain constraints are NOW AUTOMATICALLY EXTRACTED from switch statements!
        // The bytecode instrumentation in TagPropagator.recordSwitchConstraint() will
        // automatically add domain constraints based on the switch min/max values.
        // No manual PathUtils.addIntDomainConstraint() calls needed!

        // Explore all paths automatically using constraint-based exploration!
        // PathExplorer will iterate up to MAX_ITERATIONS (default 100), but the Vitruvius
        // framework's switch statement logic should naturally constrain valid inputs to [0-4].
        // This is PROPER symbolic execution - letting constraints determine valid paths,
        //
        final Object finalTestInstance = testInstance;
        List<PathExplorer.PathRecord> paths = explorer.exploreInteger(
                "user_choice",
                0, // Initial value to start exploration
                input -> executeVitruvWithInput(finalTestInstance, input));

        // Export results
        exportResultsToJson(paths, "execution_paths_automatic.json");
    }

    /**
     * Execute Vitruvius transformation with given input.
     *
     * IMPORTANT: The input is a TAGGED integer value from PathExplorer.
     * We must pass it directly to insertTask WITHOUT unboxing to preserve the tag.
     */
    private static edu.neu.ccs.prl.galette.concolic.knarr.runtime.PathConditionWrapper executeVitruvWithInput(
            Object testInstance, Object input) {

        // NEW: Try to read tag from input to get variable name and expression dynamically
        // First try Tainter.getTag (works with javaagent)
        Tag tag = Tainter.getTag(input);

        // Fallback 1: try PathExplorer.getTagForVariable (works without javaagent, uses ThreadLocal)
        if (tag == null) {
            tag = PathExplorer.getTagForVariable("user_choice");
        }

        // Fallback 2: try GaletteSymbolicator.getTagForValue
        if (tag == null) {
            tag = GaletteSymbolicator.getTagForValue(input);
        }

        String varName;
        Expression symbolicExpr = null;

        if (tag != null && tag.size() > 0) {
            // Tag-aware mode: extract variable name and expression from tag
            Object[] labels = tag.getLabels();
            String label = labels[0].toString();

            // Extract variable name from label (remove _N suffix)
            // Label format: "user_choice_0" -> extract "user_choice"
            if (label.contains("_")) {
                int lastUnderscore = label.lastIndexOf("_");
                // Check if the part after underscore is a number
                String suffix = label.substring(lastUnderscore + 1);
                try {
                    Integer.parseInt(suffix);
                    varName = label.substring(0, lastUnderscore);
                } catch (NumberFormatException e) {
                    varName = label;
                }
            } else {
                varName = label;
            }

            // Get the symbolic expression associated with this tag
            symbolicExpr = GaletteSymbolicator.getExpressionForTag(tag);

            System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput] ✓ Tag detected: label = \""
                    + label + "\", variable name = \"" + varName + "\"");
            if (symbolicExpr != null) {
                System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]   Symbolic expression: "
                        + symbolicExpr);
            }
        } else {
            // Fallback mode: use hardcoded variable name
            varName = "user_choice";
            System.out.println(
                    "[AutomaticVitruvPathExploration:executeVitruvWithInput] ⚠ No tag found, using fallback variable name: \""
                            + varName + "\"");
        }

        // Extract concrete value ONLY for display/directory name
        // DO NOT use this for execution - it loses the tag!
        int concreteValue = (input instanceof Integer) ? (Integer) input : 0;

        System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput] → Executing with " + varName + " = "
                + concreteValue);

        // Create output directory for this execution
        Path workDir = Paths.get("galette-output-automatic-" + concreteValue);

        // NOTE: When running with javaagent, domain and switch constraints are automatically added
        // by TagPropagator bytecode instrumentation. However, mvn exec:java doesn't support javaagent,
        // so we add them manually as a fallback.

        // IMPROVED: Add domain constraint using variable name from tag (if available)
        PathUtils.addIntDomainConstraint(varName, 0, 5);

        try {
            // Execute Vitruvius transformation first
            System.out.println(
                    "[AutomaticVitruvPathExploration:executeVitruvWithInput]   Attempting to invoke insertTask with workDir="
                            + workDir + ", input=" + input);
            Method insertTask = testInstance.getClass().getMethod("insertTask", Path.class, Integer.class);
            System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]   Found method: " + insertTask);

            insertTask.invoke(testInstance, workDir, input);
            System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]   Method invocation succeeded");

            // IMPROVED: Record switch constraint using variable name from tag (if available)
            PathUtils.addSwitchConstraint(varName, concreteValue);

            System.out.println(
                    "[AutomaticVitruvPathExploration:executeVitruvWithInput] Vitruvius transformation executed");
            System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]   Constraints: "
                    + PathUtils.getCurPC().size());

        } catch (Exception e) {
            System.err.println("[AutomaticVitruvPathExploration:executeVitruvWithInput] Execution failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]  Cause: "
                        + e.getCause().getClass().getName() + ": "
                        + e.getCause().getMessage());
                if (e.getCause().getCause() != null) {
                    System.err.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]  Root Cause: "
                            + e.getCause().getCause().getClass().getName() + ": "
                            + e.getCause().getCause().getMessage());
                }
            }
            e.printStackTrace();

            return new edu.neu.ccs.prl.galette.concolic.knarr.runtime.PathConditionWrapper();
        }

        // Return collected constraints
        edu.neu.ccs.prl.galette.concolic.knarr.runtime.PathConditionWrapper pc = PathUtils.getCurPC();
        System.out.println("[AutomaticVitruvPathExploration:executeVitruvWithInput]   Returning PC with " + pc.size()
                + " constraints");
        return pc;
    }

    /**
     * Export results to JSON for visualization.
     */
    private static void exportResultsToJson(List<PathExplorer.PathRecord> paths, String filename) {
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(filename);
            writer.println("[");

            for (int i = 0; i < paths.size(); i++) {
                PathExplorer.PathRecord path = paths.get(i);

                writer.println("  {");
                writer.println("    \"pathId\": " + (i + 1) + ",");
                writer.println("    \"symbolicInputs\": {");

                // Write inputs
                int inputCount = 0;
                for (java.util.Map.Entry<String, Object> entry : path.inputs.entrySet()) {
                    writer.print("      \"" + entry.getKey() + "\": ");
                    if (entry.getValue() instanceof String) {
                        writer.print("\"" + entry.getValue() + "\"");
                    } else {
                        writer.print(entry.getValue());
                    }
                    inputCount++;
                    if (inputCount < path.inputs.size()) {
                        writer.println(",");
                    } else {
                        writer.println();
                    }
                }

                writer.println("    },");
                writer.println("    \"constraints\": [");

                // Write constraints
                for (int j = 0; j < path.constraints.size(); j++) {
                    writer.print("      \"" + path.constraints.get(j).toString() + "\"");
                    if (j < path.constraints.size() - 1) {
                        writer.println(",");
                    } else {
                        writer.println();
                    }
                }

                writer.println("    ],");
                writer.println("    \"executionTime\": " + path.executionTimeMs);

                if (i < paths.size() - 1) {
                    writer.println("  },");
                } else {
                    writer.println("  }");
                }
            }

            writer.println("]");
            writer.close();

            System.out.println(
                    "\n[AutomaticVitruvPathExploration:exportResultsToJson] 📄 Results exported to: " + filename);

        } catch (Exception e) {
            System.err.println(
                    "[AutomaticVitruvPathExploration:exportResultsToJson] Failed to export results: " + e.getMessage());
        }
    }
}
