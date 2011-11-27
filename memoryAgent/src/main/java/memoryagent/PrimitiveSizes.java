package memoryagent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Source java specialist mailing list.
 */
public class PrimitiveSizes {

    /** The primitive sizes. */
    private final Map primitiveSizes = new IdentityHashMap() {
        {
            put(boolean.class, new Integer(1));
            put(byte.class, new Integer(1));
            put(char.class, new Integer(2));
            put(short.class, new Integer(2));
            put(int.class, new Integer(4));
            put(float.class, new Integer(4));
            put(double.class, new Integer(8));
            put(long.class, new Integer(8));
        }
    };

    /**
     * Gets the primitive field size.
     * 
     * @param clazz
     *            the clazz
     * @return the primitive field size
     */
    public int getPrimitiveFieldSize(Class clazz) {
        return ((Integer) primitiveSizes.get(clazz)).intValue();
    }

    /**
     * Gets the primitive array element size.
     * 
     * @param clazz
     *            the clazz
     * @return the primitive array element size
     */
    public int getPrimitiveArrayElementSize(Class clazz) {
        return getPrimitiveFieldSize(clazz);
    }

    /**
     * Gets the pointer size.
     * 
     * @return the pointer size
     */
    public int getPointerSize() {
        return 4;
    }

    /**
     * Gets the class size.
     * 
     * @return the class size
     */
    public int getClassSize() {
        return 8;
    }
}