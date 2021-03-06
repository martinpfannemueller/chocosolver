package org.clafer.choco.constraint.propagator;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.util.ESat;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.clafer.collection.Counter;

/**
 *
 * @author jimmy
 */
public class PropAtMostTransitiveClosure extends Propagator<SetVar> {

    private static final long serialVersionUID = 1L;

    private final SetVar[] relation;
    private final SetVar[] closure;
    private final boolean reflexive;

    public PropAtMostTransitiveClosure(SetVar[] relation, SetVar[] closure, boolean reflexive) {
        super(buildArray(relation, closure), PropagatorPriority.CUBIC, false);
        this.relation = relation;
        this.closure = closure;
        this.reflexive = reflexive;
    }

    private static SetVar[] buildArray(SetVar[] relation, SetVar[] closure) {
        SetVar[] array = new SetVar[relation.length + closure.length];
        System.arraycopy(relation, 0, array, 0, relation.length);
        System.arraycopy(closure, 0, array, relation.length, closure.length);
        return array;
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        for (SetVar var : relation) {
            ISetIterator iter = var.getUB().iterator();
            while(iter.hasNext()) {
                int i = iter.nextInt();
                if (i < 0 || i >= relation.length) {
                    var.remove(i, this);
                }
            }
        }

        TIntSet[] maximalClosure = maximalTransitiveClosure(relation);

        for (int i = 0; i < closure.length; i++) {
            SetVar var = closure[i];
            TIntSet reachable = maximalClosure[i];
            ISetIterator iter = var.getUB().iterator();
            while(iter.hasNext()) {
                int k = iter.nextInt();
                if ((!reflexive || i != k) && !reachable.contains(k)) {
                    var.remove(k, this);
                }
            }
        }
    }

    @Override
    public ESat isEntailed() {
        for (SetVar var : relation) {
            ISetIterator iter = var.getLB().iterator();
            while(iter.hasNext()) {
                int i = iter.nextInt();
                if (i < 0 || i >= relation.length) {
                    return ESat.FALSE;
                }
            }
        }

        TIntSet[] maximalClosure = maximalTransitiveClosure(relation);

        for (int i = 0; i < closure.length; i++) {
            SetVar var = closure[i];
            TIntSet reachable = maximalClosure[i];
            ISetIterator iter = var.getLB().iterator();
            while(iter.hasNext()) {
                int k = iter.nextInt();
                if ((!reflexive || i != k) && !reachable.contains(k)) {
                    return ESat.FALSE;
                }
            }
        }

        return isCompletelyInstantiated() ? ESat.TRUE : ESat.UNDEFINED;
    }

    @Override
    public String toString() {
        return "atMostTransitiveClosure(" + Arrays.toString(relation) + ", " + Arrays.toString(closure) + ")";
    }

    private static TIntSet[] maximalTransitiveClosure(SetVar[] relation) {
        List<TIntSet> components = computeStronglyConnectedComponents(relation);
        TIntSet[] maximalClosure = new TIntSet[relation.length];

        for (TIntSet component : components) {
            TIntSet reachable = new TIntHashSet(relation.length);

            TIntIterator iter = component.iterator();
            while (iter.hasNext()) {
                int val = iter.next();
                SetVar var = relation[val];
                assert maximalClosure[val] == null;
                maximalClosure[val] = reachable;
                ISetIterator varIter= var.getUB().iterator();
                while(varIter.hasNext()) {
                    int i = varIter.nextInt();
                    if (i >= 0 && i < relation.length) {
                        reachable.add(i);
                        TIntSet reach = maximalClosure[i];
                        if (reach != null) {
                            reachable.addAll(reach);
                        }
                    }
                }
            }
        }
        return maximalClosure;
    }

    private static List<TIntSet> computeStronglyConnectedComponents(SetVar[] relation) {
        Counter counter = new Counter();
        TIntObjectMap<Index> vertexIndices = new TIntObjectHashMap<>(relation.length);
        TIntList S = new TIntArrayList();
        List<TIntSet> components = new ArrayList<>();

        for (int vertex = 0; vertex < relation.length; vertex++) {
            if (!vertexIndices.containsKey(vertex)) {
                strongConnect(relation, vertex, counter, vertexIndices, S, components);
            }
        }
        return components;
    }

    private static Index strongConnect(SetVar[] relation, int vertex, Counter counter,
            TIntObjectMap<Index> vertexIndices, TIntList S, List<TIntSet> components) {
        int index = counter.next();
        Index vertexIndex = new Index(index, index);
        vertexIndices.put(vertex, vertexIndex);

        S.add(vertex);

        SetVar var = relation[vertex];
        ISetIterator iter = var.getUB().iterator();
        while(iter.hasNext()) {
            int neighbour = iter.nextInt();
            if (neighbour >= 0 && neighbour < relation.length) {
                Index neighbourIndex = vertexIndices.get(neighbour);
                if (neighbourIndex == null) {
                    neighbourIndex = strongConnect(relation, neighbour, counter, vertexIndices, S, components);
                    vertexIndex.setLowIndexMin(neighbourIndex.getLowIndex());
                } else if (S.contains(neighbour)) {
                    vertexIndex.setLowIndexMin(neighbourIndex.getIndex());
                }
            }
        }

        if (vertexIndex.getLowIndex() == vertexIndex.getIndex()) {
            TIntSet component = new TIntHashSet();

            int cycle;
            do {
                cycle = S.removeAt(S.size() - 1);
                component.add(cycle);
            } while (cycle != vertex);

            components.add(component);
        }
        return vertexIndex;
    }

    private static class Index {

        private final int index;
        private int lowIndex;

        Index(int index, int lowIndex) {
            this.index = index;
            this.lowIndex = lowIndex;
        }

        int getIndex() {
            return index;
        }

        int getLowIndex() {
            return lowIndex;
        }

        void setLowIndexMin(int lowIndex) {
            if (this.lowIndex >= lowIndex) {
                this.lowIndex = lowIndex;
            }
        }
    }
}
