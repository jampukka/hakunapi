package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import org.locationtech.jts.geom.Envelope;

public class FlatgeobufGeometryIndex {
    
    private static final int NODE_ITEM_LEN = 8 * 4 + 8;
    
    static int[] generateLevelBounds(int numItems, int nodeSize) {
        if (nodeSize < 2)
            throw new RuntimeException("Node size must be at least 2");
        if (numItems == 0)
            throw new RuntimeException("Number of items must be greater than 0");

        // number of nodes per level in bottom-up order
        int n = numItems;
        int numNodes = n;
        ArrayList<Integer> levelNumNodes = new ArrayList<Integer>();
        levelNumNodes.add(n);
        do {
            n = (n + nodeSize - 1) / nodeSize;
            numNodes += n;
            levelNumNodes.add(n);
        } while (n != 1);

        // offsets per level in reversed storage order (top-down)
        ArrayList<Integer> levelOffsets = new ArrayList<Integer>();
        n = numNodes;
        for (int size : levelNumNodes)
            levelOffsets.add(n -= size);
        
        int[] levelBounds = new int[levelNumNodes.size()];
        // bounds per level in reversed storage order (top-down)
        for (int i = 0; i < levelNumNodes.size(); i++)
            levelBounds[i] = levelOffsets.get(i) + levelNumNodes.get(i);
        return levelBounds;
    }
    
    public static LongStream bboxStream(ByteBuffer bb, int start, int numItems, int nodeSize, Envelope rect) {
        double minX = rect.getMinX();
        double minY = rect.getMinY();
        double maxX = rect.getMaxX();
        double maxY = rect.getMaxY();
        int[] levelBounds = generateLevelBounds(numItems, nodeSize);
        int numNodes = levelBounds[0];
        
        IntPairStack queue = new IntPairStack();
        queue.add(0,  levelBounds.length - 1);
        
        ListOfLong buf = new ListOfLong();
        
        Spliterator.OfLong spliterator = new Spliterator.OfLong() {

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return DISTINCT | IMMUTABLE | NONNULL;
            }

            @Override
            public OfLong trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (!buf.isEmpty()) {
                    action.accept(buf.pop());
                    return true;
                }
                if (queue.isEmpty()) {
                    return false;
                }
                int level = queue.pop();
                int nodeIndex = queue.pop();
                boolean isLeafNode = nodeIndex >= numNodes - numItems;
                // find the end index of the node
                int levelEnd = levelBounds[level];
                int end = Math.min(nodeIndex + nodeSize, levelEnd);
                int nodeStart = start + (nodeIndex * NODE_ITEM_LEN);
                // int length = end - nodeIndex;
                // search through child nodes
                for (int pos = nodeIndex; pos < end; pos++) {
                    int offset = nodeStart + ((pos - nodeIndex) * NODE_ITEM_LEN);
                    double nodeMinX = bb.getDouble(offset + 0);
                    double nodeMinY = bb.getDouble(offset + 8);
                    double nodeMaxX = bb.getDouble(offset + 16);
                    double nodeMaxY = bb.getDouble(offset + 24);
                    if (maxX < nodeMinX || maxY < nodeMinY || minX > nodeMaxX || minY > nodeMaxY) {
                        continue;
                    }
                    long indexOffset = bb.getLong(offset + 32);
                    if (isLeafNode) {
                        buf.add(indexOffset);
                    } else if (minX <= nodeMinX && minY <= nodeMinY && maxX >= nodeMaxX && maxY >= nodeMaxY) {
                        all(bb, start, numItems, nodeSize, levelBounds, (int) indexOffset, level - 1, buf::add);
                    } else {
                        queue.add((int) indexOffset, level - 1);
                    }
                }
                return tryAdvance(action);
            }
            
            @Override
            public void forEachRemaining(LongConsumer action) {
                if (!buf.isEmpty()) {
                    action.accept(buf.pop());
                }
                while (!queue.isEmpty()) {
                    int level = queue.pop();
                    int nodeIndex = queue.pop();
                    boolean isLeafNode = nodeIndex >= numNodes - numItems;
                    // find the end index of the node
                    int levelEnd = levelBounds[level];
                    int end = Math.min(nodeIndex + nodeSize, levelEnd);
                    int nodeStart = start + (nodeIndex * NODE_ITEM_LEN);
                    // int length = end - nodeIndex;
                    // search through child nodes
                    for (int pos = nodeIndex; pos < end; pos++) {
                        int offset = nodeStart + ((pos - nodeIndex) * NODE_ITEM_LEN);
                        double nodeMinX = bb.getDouble(offset + 0);
                        double nodeMinY = bb.getDouble(offset + 8);
                        double nodeMaxX = bb.getDouble(offset + 16);
                        double nodeMaxY = bb.getDouble(offset + 24);
                        if (maxX < nodeMinX || maxY < nodeMinY || minX > nodeMaxX || minY > nodeMaxY) {
                            continue;
                        }
                        long indexOffset = bb.getLong(offset + 32);
                        if (isLeafNode) {
                            action.accept(indexOffset);
                        } else if (minX <= nodeMinX && minY <= nodeMinY && maxX >= nodeMaxX && maxY >= nodeMaxY) {
                            all(bb, start, numItems, nodeSize, levelBounds, (int) indexOffset, level - 1, action);
                        } else {
                            queue.add((int) indexOffset, level - 1);
                        }
                    }
                }
            }
            
        };
        return StreamSupport.longStream(spliterator, false);
    }

    public static long[] bbox(ByteBuffer bb, int start, int numItems, int nodeSize, Envelope rect) {
        double minX = rect.getMinX();
        double minY = rect.getMinY();
        double maxX = rect.getMaxX();
        double maxY = rect.getMaxY();
        int[] levelBounds = generateLevelBounds(numItems, nodeSize);
        int numNodes = levelBounds[0];

        ListOfLong result = new ListOfLong();
        IntPairStack queue = new IntPairStack();
        queue.add(0,  levelBounds.length - 1);
        while (!queue.isEmpty()) {
            int level = queue.pop();
            int nodeIndex = queue.pop();
            boolean isLeafNode = nodeIndex >= numNodes - numItems;
            // find the end index of the node
            int levelEnd = levelBounds[level];
            int end = Math.min(nodeIndex + nodeSize, levelEnd);
            int nodeStart = start + (nodeIndex * NODE_ITEM_LEN);
            // int length = end - nodeIndex;
            // search through child nodes
            for (int pos = nodeIndex; pos < end; pos++) {
                int offset = nodeStart + ((pos - nodeIndex) * NODE_ITEM_LEN);
                double nodeMinX = bb.getDouble(offset + 0);
                double nodeMinY = bb.getDouble(offset + 8);
                double nodeMaxX = bb.getDouble(offset + 16);
                double nodeMaxY = bb.getDouble(offset + 24);
                if (maxX < nodeMinX || maxY < nodeMinY || minX > nodeMaxX || minY > nodeMaxY) {
                    continue;
                }
                long indexOffset = bb.getLong(offset + 32);
                if (isLeafNode) {
                    result.add(indexOffset);
                } else if (minX <= nodeMinX && minY <= nodeMinY && maxX >= nodeMaxX && maxY >= nodeMaxY) {
                    all(bb, start, numItems, nodeSize, levelBounds, (int) indexOffset, level - 1, result::add);
                } else {
                    queue.add((int) indexOffset, level - 1);
                }
            }
        }

        return result.toArray();
    }
    
    private static void all(ByteBuffer bb, int start, int numItems, int nodeSize, int[] levelBounds, int nodeIndex, int level, LongConsumer consumer) {
        int numNodes = levelBounds[0];
        
        IntPairStack queue = new IntPairStack();
        queue.add(nodeIndex, level);

        while (!queue.isEmpty()) {
            level = queue.pop();
            nodeIndex = queue.pop();
            boolean isLeafNode = nodeIndex >= numNodes - numItems;
            int levelEnd = levelBounds[level];
            int end = Math.min(nodeIndex + nodeSize, levelEnd);
            int nodeStart = start + (nodeIndex * NODE_ITEM_LEN);
            for (int pos = nodeIndex; pos < end; pos++) {
                int offset = nodeStart + ((pos - nodeIndex) * NODE_ITEM_LEN);
                long indexOffset = bb.getLong(offset + 32);
                if (isLeafNode) {
                    consumer.accept(indexOffset);
                } else {
                    queue.add((int) indexOffset, level - 1);
                }
            }
        }
    }
    
    private static class ListOfLong {
        
        private int n;
        private long[] arr = new long[16];
        
        public void add(long v) {
            if (n == arr.length) {
                arr = Arrays.copyOf(arr, n * 2);
            }
            arr[n++] = v;
        }
        
        public boolean isEmpty() {
            return n == 0;
        }
        
        public long pop() {
            return arr[--n];
        }
        
        public long[] toArray() {
            return n == arr.length ? arr : Arrays.copyOf(arr, n);
        }
        
    }
    
    private static class IntPairStack {
        
        private int n;
        private int[] arr = new int[16];
        
        public void add(int a, int b) {
            if (n == arr.length) {
                arr = Arrays.copyOf(arr, n * 2);
            }
            arr[n++] = a;
            arr[n++] = b;
        }
        
        public boolean isEmpty() {
            return n == 0;
        }
        
        public int pop() {
            return arr[--n];
        }
        
    }

}
