package edaniel.jtracer;

import org.bridj.Pointer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class OpenCLObject {

    private static HashMap<Class<?>, Integer> COMPONENT_SIZE;

    private List<Copier> mCopier = new LinkedList<>();
    private Pointer<Byte>   mPointer;

    protected void init() {
        int size = 0;

        try {
            for (Field field : getAllDeclaredFields(new LinkedList<>(), this.getClass())) {
                if (field.isAnnotationPresent(OpenCLField.class)) {
                    final Object obj = field.get(this); // could throw IllegalAccessException

                    size += handleFieldObject(field, obj, size);
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        size = getAlignedSize(this.getClass(), size);
        mPointer = Pointer.allocateArray(Byte.class, size);
    }

    public void recreateOpenCLObject() {
        mCopier.clear();
        if (mPointer != null)
            mPointer.release();
        mPointer = null;
        init();
        update();
    }

    // return size of the field
    private int handleFieldObject(final Field field, final Object object, final int offset) {
        if (object == null)
            return 0;

        int size = 0;
        // create a copier depending of the object type, the copier will be in charge to copy the object value
        // to the main pointer at the given offset

        if (object instanceof Float) {
            size += COMPONENT_SIZE.get(Float.class);
            mCopier.add(p -> ((ByteBuffer)p.getBuffer()).putFloat(offset, (Float) getFieldInstance(field)));
        }
        else if (object instanceof Integer) {
            size += COMPONENT_SIZE.get(Integer.class);
            mCopier.add(p -> ((ByteBuffer)p.getBuffer()).putInt(offset, (Integer) getFieldInstance(field)));
        }
        if (object.getClass().isArray()) {
            if (Array.getLength(object) > 0) {
                Object o = Array.get(object, 0);
                if (!isCompatibleObject(o))
                    throw new RuntimeException("Cannot init "+getClass()+" incompatible array "+field.getName());
                size += getAlignedSize(field, Array.getLength(object) * getObjectSize(o));
                if (o instanceof OpenCLObject) {
                    mCopier.add(p -> {
                        int stride = getObjectSize(o);
                        int position = offset;
                        for (int i = 0; i < Array.getLength(object) ; i++) {
                            Pointer<Byte> pointer = ((OpenCLObject)Array.get(object, i)).getPointer();
                            p.setBytesAtOffset(position, (ByteBuffer) pointer.getBuffer(), 0, pointer.getValidElements());
                            position += stride;
                        }
                    });
                }
                else {
                    if (object instanceof float[])
                        mCopier.add((p) -> copyFloatArray(p, offset, (float[]) object));
                    else if (object instanceof int[])
                        mCopier.add(p -> p.setIntsAtOffset(offset, (int[]) object));
                    else if (object instanceof Integer[])
                        mCopier.add(p -> copyIntegerArrayAtOfftet(p, offset, (Integer[]) object));
                    else
                        throw new RuntimeException("Incompatible array of type "+object.getClass()+" on field "+field);
                }
            }
//            else {
//                Integer s = COMPONENT_SIZE.get(object.getClass().getComponentType());
//                if (s == null)
//                    throw new UnsupportedOperationException("The component of type " + object.getClass() + " is unknown or not compatible");
//                size += getAlignedSize(field, s * Array.getLength(object));
//            }
        }
        else if (object instanceof List) {
            List<?> list = (List<?>) object;
            if (!list.isEmpty()) {
                Object o = list.get(0);
                if (!isCompatibleObject(o))
                    throw new RuntimeException("Cannot init "+getClass()+" incompatible list "+list);
                int objectSize = getObjectSize(o);
                size += list.size() * getObjectSize(o);

            }
        }
        else if (object instanceof OpenCLObject) {
            size += ((OpenCLObject)getFieldInstance(field)).getPointer().getValidBytes();
            mCopier.add(p -> p.setBytesAtOffset(offset, ((OpenCLObject)getFieldInstance(field)).getPointer().getBuffer()));
        }

        return size;
    }

    private List<Field> getAllDeclaredFields(List<Field> list, Class<?> type) {
        list.addAll(0, Arrays.asList(type.getDeclaredFields()));

        if (type.getSuperclass() != null)
            getAllDeclaredFields(list, type.getSuperclass());

        return list;
    }

    private Object getFieldInstance(Field field) {
        try {
            return field.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot copy memory of field "+field, e.getCause());
        }
    }

    private < T > int getAlignedSize(Class<T> clazz, int size) {
        if (clazz.isAnnotationPresent(Alignment.class)) {
            int alignment = this.getClass().getAnnotation(Alignment.class).alignment();
            int alignedSize = ((size + alignment - 1) / alignment) * alignment;
            return alignedSize;
        }
        return size;
    }

    private int getAlignedSize(Field field, int size) {
        if (field.isAnnotationPresent(Alignment.class)) {
            int alignment = field.getAnnotation(Alignment.class).alignment();
            int alignedSize = ((size + alignment - 1) / alignment) * alignment;
            return alignedSize;
        }
        return size;
    }

    private int getObjectSize(Object o) {
        if (COMPONENT_SIZE.containsKey(o.getClass()))
            return COMPONENT_SIZE.get(o.getClass());
        if (o.getClass().isArray())
            return getObjectSize(o.getClass().getComponentType());
        if (o instanceof OpenCLObject)
            return (int) ((OpenCLObject)o).getPointer().getValidBytes();
        return 0;
    }

    private void update() {
        mCopier.forEach((copier -> copier.copy(mPointer)));
    }

    Pointer<Byte> getPointer() {
        update();
        return mPointer;
    }

    private void copyIntegerArrayAtOfftet(Pointer<?> p, int offset, Integer[] array) {
        if (array.length == 0)
            return;
        int elemSize = getObjectSize(array[0]);
        ByteBuffer buffer = p.getByteBuffer().position(offset);
        for (int i = 0; i < array.length; i++) {
            buffer.putInt(array[i]);
        }
    }

    private void copyFloatArray(Pointer<?> p, final int offset, final float[] v) {
        p.setFloatsAtOffset(offset, v);
    }

    private void copyList(Pointer<?> p, final int offset, final List<?> list, final int componentSize) {

    }

    private interface Copier {
        void copy(Pointer<?> pointer);
    }

    private static boolean isCompatibleObject(Object object) {
        if (COMPONENT_SIZE.containsKey(object.getClass()))
            return true;
        else if (object instanceof OpenCLObject)
            return true;
        else if (object instanceof List)
            return true;
        else if (object.getClass().isArray())
            return true;
        return false;
    }

    static {
        COMPONENT_SIZE = new HashMap<>();
        COMPONENT_SIZE.put(int.class, 4);
        COMPONENT_SIZE.put(Integer.class, 4);
        COMPONENT_SIZE.put(Float.class, 4);
        COMPONENT_SIZE.put(float.class, 4);
    }
}
