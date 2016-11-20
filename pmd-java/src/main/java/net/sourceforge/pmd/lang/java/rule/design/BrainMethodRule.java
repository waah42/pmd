package net.sourceforge.pmd.lang.java.rule.design;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.rule.JavaRuleViolation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LOC        = Lines of Code
 * NOAV       = Number Of Accessed Variables
 * CYCLO      = Number of decision points
 * MAXNESTING = The maximum nesting level of control structures within a method.
 * Condition  = LOC > high(class)/2 && CYCLO >= high && MAXNESTING >= several && NOAV > many
 */


@SuppressWarnings("unused")
public class BrainMethodRule extends AbstractJavaRule {
    private static final int CYCLO_THRESHOLD = 4;
    private static final int MAXNESTING_THRESHOLD = 3;
    private static final int NOAV_THRESHOLD = 5;
    private static final int LOC_THRESHOLD = 65;


    private int LOC, CYCLO, NOAV;

    // different combination of AST's is used for max nesting and cyclo
    private List<Class> astListForMaxNesting, astListForCyclo;

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {

        init();
        return super.visit(node, data);

    }


    private void init() {
        LOC = 0;
        CYCLO = 0;
        NOAV = 0;
        astListForMaxNesting = new ArrayList<>();
        astListForCyclo = new ArrayList<>();

        astListForMaxNesting.addAll(Arrays.asList(ASTForStatement.class, ASTIfStatement.class,
                ASTWhileStatement.class, ASTTryStatement.class, ASTSwitchStatement.class));

        astListForCyclo.addAll(Arrays.asList(ASTForStatement.class, ASTIfStatement.class,
                ASTWhileStatement.class, ASTCatchStatement.class, ASTDoStatement.class, ASTConditionalExpression.class));

    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        LOC = node.getEndLine() - node.getBeginLine();
        NOAV = node.findDescendantsOfType(ASTVariableDeclarator.class).size();
        CYCLO = calculateCYCLO(node);

        if ((LOC > LOC_THRESHOLD) && (CYCLO > CYCLO_THRESHOLD) && checkMAXNESTING(node) && (NOAV > NOAV_THRESHOLD)) {
            RuleContext ctx = (RuleContext) data;
            ctx.getReport().addRuleViolation(new JavaRuleViolation(this, ctx, node, "Method Name=" + node.getName()));
        }
        return super.visit(node, data);
    }

    private boolean checkMAXNESTING(ASTMethodDeclaration astMethodDeclaration) {

        for (Class anAstListForMaxNesting : astListForMaxNesting) {

            for (Object object : astMethodDeclaration.findDescendantsOfType(anAstListForMaxNesting)) {

                Node node = (Node) object;
                int total = node.getParentsOfType(ASTIfStatement.class).size() + node.getParentsOfType(ASTWhileStatement.class).size()
                        + node.getParentsOfType(ASTForStatement.class).size() + node.getParentsOfType(ASTSwitchStatement.class).size()
                        + node.getParentsOfType(ASTTryStatement.class).size();
                if (total > MAXNESTING_THRESHOLD)
                    return true;
            }
        }

        return false;
    }


    private int calculateCYCLO(ASTMethodDeclaration astMethodDeclaration) {
        int cycloCounter = 0;
        // if the method has more then 0 children than one path will always be available.
        if (astMethodDeclaration.jjtGetNumChildren() > 0)
            cycloCounter++;

        for (Class anAstListForCyclo : astListForCyclo) {

            //for conditional expression we need to check if it's ternary
            if (anAstListForCyclo != ASTConditionalExpression.class) {
                cycloCounter += astMethodDeclaration.findDescendantsOfType(anAstListForCyclo).size();
            } else {
                for (Object object : astMethodDeclaration.findDescendantsOfType(anAstListForCyclo)) {
                    ASTConditionalExpression node = (ASTConditionalExpression) object;
                    if (node.isTernary())
                        cycloCounter++;
                }
            }

        }

        // for switch we need to count it's cases too
        List<ASTSwitchStatement> conditionStatements = astMethodDeclaration.findDescendantsOfType(ASTSwitchStatement.class);
        for (ASTSwitchStatement node : conditionStatements) {
            // +1 because switch is a decision point too
            cycloCounter++;

            // counting number of cases in the switch

            int childCount = node.jjtGetNumChildren();
            int lastIndex = childCount - 1;
            for (int i = 0; i < lastIndex; i++) {
                Node childNode = node.jjtGetChild(i);
                if (childNode instanceof ASTSwitchLabel) {
                    ASTSwitchLabel label = (ASTSwitchLabel) childNode;

                    // default is not considered a decision
                    if (!label.isDefault())
                        cycloCounter++;
                }

            }

        }

        return cycloCounter;
    }


}
