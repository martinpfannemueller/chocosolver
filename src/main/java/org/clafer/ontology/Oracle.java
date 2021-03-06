package org.clafer.ontology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.clafer.domain.Domain;
import org.clafer.math.SetEnvironment;
import org.clafer.math.SetTheory;

/**
 * An oracle for answering queries of is-a and has-a relationships.
 *
 * @author jimmy
 */
public class Oracle {

    private final Relation<Concept> isA;
    private final Relation<Concept> hasA;
    private final Relation<Concept> aHas;

    private final IdMap<Path> idMap = new IdMap<>();
    private final SetTheory theory = new SetTheory();
    private int tempId = -1;

    public Oracle(
            Relation<Concept> isA,
            Relation<Concept> hasA,
            ConstraintDatabase constraints,
            List<ConstraintDatabase[]> disjunctions) {
        this.isA = isA.transitiveClosureWithoutCycles();
        this.hasA = this.isA.compose(hasA);
        this.aHas = this.isA.compose(this.hasA.inverse());

        compile(constraints, theory);
        for (ConstraintDatabase[] disjunction : disjunctions) {
            SetEnvironment[] ors = new SetEnvironment[disjunction.length];
            for (int i = 0; i < disjunction.length; i++) {
                ors[i] = SetTheory.or();
                compile(disjunction[i], ors[i]);
            }
            theory.constructiveDisjunction(ors);
        }

        new ArrayList<>(idMap.keySet()).forEach(this::addPathConstraints);
    }

    private void compile(ConstraintDatabase database, SetEnvironment theory) {
        for (Entry<Path, Domain> assignment : database.assignments.entrySet()) {
            Path path = assignment.getKey();
            Domain value = assignment.getValue();
            for (Path groundPath : groundPaths(path)) {
                theory.subset(idMap.getId(groundPath), value);
            }
        }

        for (Entry<Path, Set<Path>> equality1 : database.equalities.entrySet()) {
            Path path1 = equality1.getKey();
            List<Path> groundPaths1 = groundPaths(path1);
            int[] groundPaths1Id = idMap.getIds(groundPaths1);
            for (Path path2 : equality1.getValue()) {
                int[] groundPaths2Id = idMap.getIds(groundPaths(path2));
                unionEqual(theory, groundPaths1Id, groundPaths2Id);
            }
        }

        for (Entry<Path, Set<Path>> equality1 : database.localEqualities.entrySet()) {
            Path path1 = equality1.getKey();
            List<Path> groundPaths1 = groundPaths(path1);
            Map<Concept, List<Path>> groundPathsMap1
                    = groundPaths1.stream().collect(Collectors.groupingBy(Path::getContext));
            for (Path path2 : equality1.getValue()) {
                Map<Concept, List<Path>> groundPathsMap2
                        = groundPaths(path2).stream().collect(Collectors.groupingBy(Path::getContext));
                for (Concept concept : groundPathsMap1.keySet()) {
                    int[] groundPaths1Id = idMap.getIds(groundPathsMap1.get(concept));
                    int[] groundPaths2Id = idMap.getIds(groundPathsMap2.get(concept));
                    unionEqual(theory, groundPaths1Id, groundPaths2Id);
                }
            }
        }

    }

    private void unionEqual(SetEnvironment theory, int[] union1, int[] union2) {
        if (union1.length == 1) {
            theory.union(union1[0], union2);
        } else if (union2.length == 1) {
            theory.union(union2[0], union1);
        } else {
            theory.union(tempId, union1);
            theory.union(tempId, union2);
            tempId--;
        }
    }

    private void addPathConstraints(Path path) {
        groundPaths(path).forEach(this::addGroundPathConstraints);
    }

    private void addGroundPathConstraints(Path groundPath) {
        Path cur = groundPath;
        while (cur.length() > 1) {
            Path next = cur.dropPrefix(1);
            theory.
                    union(idMap.getId(next),
                            aHas.from(next.getContext()).stream()
                            .filter(x -> isGround(x, isA))
                            .map(next::prepend).mapToInt(idMap::getId).toArray());
            cur = next;
        }
    }

    public boolean isA(Concept sub, Concept sup) {
        return isA.has(sub, sup);
    }

    public boolean hasA(Concept parent, Concept child) {
        return hasA.has(parent, child);
    }

    public Domain getAssignment(Concept... steps) {
        return getAssignment(new Path(steps));
    }

    /**
     * Returns the envelope of values the path must take.
     *
     * @param path
     * @return the envelope of values the path must take, or null if unbounded
     */
    public Domain getAssignment(Path path) {
        addPathConstraints(path);
        theory.union(idMap.getId(path), idMap.getIds(groundPaths(path)));
        theory.propagate();
        return theory.getEnv(idMap.getId(path));
    }

    public void newAssignment(Path path, Domain value) {
        theory.subset(idMap.getId(path), value);
    }

    public boolean propagate() {
        return theory.propagate();
    }

    private ArrayList<Path> groundPaths(Path path) {
        ArrayList<Path> out = new ArrayList<>();
        groundPaths(path.getSteps(), 0, out);
        return out;
    }

    private void groundPaths(Concept[] steps, int index, List<Path> out) {
        if (index == steps.length) {
            out.add(new Path(steps));
        } else {
            Collection<Concept> subs = isA.to(steps[index]);
            for (Concept sub : subs) {
                if (isGround(sub, isA)) {
                    Concept[] alter = steps.clone();
                    alter[index] = sub;
                    groundPaths(alter, index + 1, out);
                }
            }
        }
    }

    private static boolean isGround(Concept concept, Relation<Concept> isA) {
        Set<Concept> subs = isA.to(concept);
        return subs.size() == 1 && subs.contains(concept);
    }
}
