package tech.pantheon.triemap;

import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class EntrySetSpliterator<K, V, M extends TrieMap<K, V>> implements Spliterator<Entry<K, V>> {

    static final int SPLIT_THRESHOLD = 1024;
    private final int characteristics;
    private final Entry<K, V>[] entries;
    private final AtomicInteger currentIndex = new AtomicInteger();
    private final AtomicInteger lastIndex = new AtomicInteger();

    EntrySetSpliterator(final M map, final int characteristics) {
        this.characteristics = characteristics;
        this.entries = toArray(map);
        this.currentIndex.set(0);
        this.lastIndex.set(entries.length - 1);
    }

    private EntrySetSpliterator(final Entry<K, V>[] entries, final int startIndex, final int endIndex,
        final int characteristics) {
        this.entries = entries;
        this.characteristics = characteristics;
        this.currentIndex.set(startIndex);
        this.lastIndex.set(endIndex);
    }

    @Override
    public boolean tryAdvance(Consumer<? super Entry<K, V>> consumer) {
        requireNonNull(consumer);
        final int index = currentIndex.getAndIncrement();
        if (index > lastIndex.get()) {
            return false;
        }
        consumer.accept(entries[index]);
        return true;
    }

    @Override
    public Spliterator<Entry<K, V>> trySplit() {
        final int remains = lastIndex.get() - currentIndex.get() + 1;
        if (remains < SPLIT_THRESHOLD) {
            return null;
        }
        final int splitLastIndex = lastIndex.get();
        final int splitStartIndex = lastIndex.addAndGet(- remains /2) +1;
        return new EntrySetSpliterator<>(entries, splitStartIndex, splitLastIndex, characteristics);
    }

    @Override
    public long estimateSize() {
        return lastIndex.get() - currentIndex.get() + 1;
    }

    @Override
    public int characteristics() {
        return characteristics;
    }

    private <K, V, M extends TrieMap<K, V>> Entry<K, V>[] toArray(M map) {
        final var list = new LinkedList<Entry<K, V>>();
        collectEntries(list, map, map.readRoot());
        return list.toArray(Entry[]::new);
    }

    private static <V, K, M extends TrieMap<K, V>> void collectEntries(final List<Entry<K, V>> collection, final M map,
        final INode<K, V> in) {
        final var mn = in.gcasRead(map);
        if (mn instanceof CNode cn) {
            for (var elem : cn.array) {
                if (elem instanceof SNode sn) {
                    collection.add(sn);
                } else if (elem instanceof INode sub) {
                    collectEntries(collection, map, sub);
                }
            }
        } else if (mn instanceof TNode tn) {
            collection.add(tn);
        } else if (mn instanceof LNode ln) {
            final var lnEntries = ln.entries();
            LNodeEntries<K, V> elm;
            while ((elm = lnEntries.next()) != null) {
                collection.add(elm);
            }
        }
    }
}
