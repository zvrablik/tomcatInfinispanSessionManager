package memoryagent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import memoryagent.SizeOfAgent;

/**
 * Source java specialist mailing list.
 */
public final class ObjectMemoryCounter {

    /** The Constant sizes. */
    private static final PrimitiveSizes sizes = new PrimitiveSizes();

    /** The visited. */
    private final Map visited = new IdentityHashMap();

    /** The stack. */
    private final Stack stack = new Stack();

    /**
     * Estimate.
     * 
     * @param obj
     *            the obj
     * @return the long
     */
    public synchronized long estimate(Object obj) {
        assert visited.isEmpty();
        assert stack.isEmpty();
        long result = _estimate(obj);
        while (!stack.isEmpty()) {
            result += _estimate(stack.pop());
        }
        visited.clear();
        return result;
    }

    /**
     * Skip object.
     * 
     * @param obj
     *            the obj
     * @return true, if successful
     */
    private boolean skipObject(Object obj) {
        if (obj instanceof String) {
            // this will not cause a memory leak since
            // unused interned Strings will be thrown away
            if (obj == ((String) obj).intern()) {
                return true;
            }
        }
        return (obj == null) || visited.containsKey(obj);
    }

    /**
     * _estimate.
     * 
     * @param obj
     *            the obj
     * @return the long
     */
    private long _estimate(Object obj) {
        if (skipObject(obj))
            return 0;
        visited.put(obj, null);
        long result = 0;
        Class clazz = obj.getClass();
        if (clazz.isArray()) {
            return _estimateArray(obj);
        }
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (!Modifier.isStatic(fields[i].getModifiers())) {
                    if (fields[i].getType().isPrimitive()) {
                        result += sizes.getPrimitiveFieldSize(fields[i]
                                .getType());
                    } else {
                        result += sizes.getPointerSize();
                        fields[i].setAccessible(true);
                        try {
                            Object toBeDone = fields[i].get(obj);
                            if (toBeDone != null) {
                                stack.add(toBeDone);
                            }
                        } catch (IllegalAccessException ex) {
                            assert false;
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        result += sizes.getClassSize();
        return roundUpToNearestEightBytes(result);
    }

    /**
     * Round up to nearest eight bytes.
     * 
     * @param result
     *            the result
     * @return the long
     */
    private long roundUpToNearestEightBytes(long result) {
        if ((result % 8) != 0) {
            result += 8 - (result % 8);
        }
        return result;
    }

    /**
     * _estimate array.
     * 
     * @param obj
     *            the obj
     * @return the long
     */
    protected long _estimateArray(Object obj) {
        long result = 16;
        int length = Array.getLength(obj);
        if (length != 0) {
            Class arrayElementClazz = obj.getClass().getComponentType();
            if (arrayElementClazz.isPrimitive()) {
                result += length
                        * sizes.getPrimitiveArrayElementSize(arrayElementClazz);
            } else {
                for (int i = 0; i < length; i++) {
                    result += sizes.getPointerSize()
                            + _estimate(Array.get(obj, i));
                }
            }
        }
        return result;
    }

    /**
     * Get memory size. Use java 6 agent or estiamate if agent is not
     * configured.
     * 
     * @param obj
     * @return
     */
    public static long getMemorySize(Object obj) {
        long size = -10;
        try {
            size = SizeOfAgent.fullSizeOf(obj);
        } catch (Exception ex) {
            System.out
                    .println("[ObjectMemoryCounter] memory size agent is not set. Estimation used instead.");
            size = estimateMemorySize(obj);
        }

        return size;
    }

    /**
     * Create size per item.
     * 
     * @param toMeasure
     * @param denominator
     * @return key, size (in bytes)
     */
    public static Map<String, Double> getMemorySizePerItem(
            Map<String, Object> toMeasure, double denominator) {
        Set<Entry<String, Object>> entrySet = toMeasure.entrySet();

        Map<String, Double> sizeValues = new HashMap<String, Double>();

        for (Entry<String, Object> item : entrySet) {
            long memorySize = ObjectMemoryCounter
                    .getMemorySize(item.getValue());
            sizeValues.put(item.getKey(), memorySize/denominator);
        }

        return sizeValues;
    }

    /**
     * Estimate memory consumption. Don't use agent to compute memory
     * consumption.
     * 
     * @param obj
     * @return
     */
    public static long estimateMemorySize(Object obj) {
        long size;
        size = new ObjectMemoryCounter().estimate(obj);
        return size;
    }
}
