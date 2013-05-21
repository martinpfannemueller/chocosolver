package org.clafer.ast.analysis;

import gnu.trove.list.array.TIntArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.clafer.ast.AstAbstractClafer;
import org.clafer.ast.AstClafer;
import org.clafer.ast.AstConcreteClafer;
import org.clafer.ast.AstModel;
import org.clafer.ast.Card;

/**
 *
 * @author jimmy
 */
public class PartialSolutionAnalysis {

    private PartialSolutionAnalysis() {
    }

    public static Map<AstClafer, PartialSolution> analyze(
            AstModel model,
            Map<AstClafer, Card> globalCards,
            Map<AstClafer, Format> formats,
            Map<AstAbstractClafer, Offsets> offsets) {
        Map<AstClafer, PartialSolution> partialSolutions = new HashMap<AstClafer, PartialSolution>();

        partialSolutions.put(model, new PartialSolution(new boolean[]{true}, new int[0][]));
        for (AstConcreteClafer child : model.getChildren()) {
            analyze(child, globalCards, formats, partialSolutions);
        }
        for (AstAbstractClafer abstractClafer : model.getAbstractClafers()) {
            analyze(abstractClafer, globalCards, formats, offsets, partialSolutions);
        }

        return partialSolutions;
    }

    private static void analyze(
            AstAbstractClafer clafer,
            Map<AstClafer, Card> globalCards,
            Map<AstClafer, Format> formats,
            Map<AstAbstractClafer, Offsets> offsets,
            Map<AstClafer, PartialSolution> partialSolutions) {
        Card globalCard = AnalysisUtil.notNull(clafer + " global card not analyzed yet", globalCards.get(clafer));
        boolean[] solution = new boolean[globalCard.getHigh()];
        int[][] parents = new int[globalCard.getHigh()][];
        for (AstClafer sub : clafer.getSubs()) {
            int offset = AnalysisUtil.notNull(clafer + " offset not analyzed yet", offsets.get(clafer)).getOffset(sub);

            PartialSolution partialSubSolution = partialSolutions.get(sub);
            // This is possible for partialSubSolution to be null if a child of an abstract
            // extends the abstract. Assume the worst possible case by assuming it is empty.
            if (partialSubSolution != null) {
                System.arraycopy(partialSubSolution.getSolution(), 0, solution, offset, partialSubSolution.size());
            }
        }
        partialSolutions.put(clafer, new PartialSolution(solution, parents));

        for (AstConcreteClafer child : clafer.getChildren()) {
            analyze(child, globalCards, formats, partialSolutions);
        }
    }

    private static void analyze(
            AstConcreteClafer clafer,
            Map<AstClafer, Card> globalCards,
            Map<AstClafer, Format> formats,
            Map<AstClafer, PartialSolution> partialSolutions) {
        Card globalCard = AnalysisUtil.notNull(clafer + " global card not analyzed yet", globalCards.get(clafer));
        Format format = AnalysisUtil.notNull(clafer.getName() + " format not analyzed yet", formats.get(clafer));

        boolean[] solution = new boolean[globalCard.getHigh()];
        TIntArrayList[] parents = new TIntArrayList[globalCard.getHigh()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = new TIntArrayList();
        }

        PartialSolution partialParentSolution = partialSolutions.get(clafer.getParent());
        int lowCard = clafer.getCard().getLow();
        int highCard = clafer.getCard().getHigh();
        switch (format) {
            case LowGroup:
                Arrays.fill(solution, 0, globalCard.getLow(), true);
                int low = 0;
                int high = highCard;
                for (int i = 0; i < partialParentSolution.size(); i++) {
                    for (int j = low; j < high && j < parents.length; j++) {
                        parents[j].add(i);
                    }
                    if (partialParentSolution.hasClafer(i)) {
                        low += lowCard;
                    }
                    high += highCard;
                }
                break;
            case ParentGroup:
                assert lowCard == highCard;
                for (int i = 0; i < partialParentSolution.size(); i++) {
                    for (int j = 0; j < lowCard; j++) {
                        solution[i * lowCard + j] = partialParentSolution.hasClafer(i);
                        parents[i * lowCard + j].add(i);
                    }
                }
                break;
            default:
                throw new AnalysisException();
        }
        partialSolutions.put(clafer, new PartialSolution(solution, toArray(parents)));

        for (AstConcreteClafer child : clafer.getChildren()) {
            analyze(child, globalCards, formats, partialSolutions);
        }
    }

    private static int[][] toArray(TIntArrayList[] list) {
        int[][] array = new int[list.length][];
        for (int i = 0; i < array.length; i++) {
            array[i] = list[i].toArray();
        }
        return array;
    }
}
