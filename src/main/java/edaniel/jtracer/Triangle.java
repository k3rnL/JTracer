package edaniel.jtracer;

public class Triangle extends SceneObject  {

    @OpenCLField
    @Alignment(alignment = 16)
    public float[] v1 = new float[3];

    @OpenCLField
    @Alignment(alignment = 16)
    public float[] v2 = new float[3];

    @OpenCLField
    @Alignment(alignment = 16)
    public float[] v3 = new float[3];

    public Triangle() {
        super(Type.SPHERE);
        init();
    }

    public Triangle(float[] vertexes, int alignment) {
        super(Type.SPHERE);
        System.arraycopy(vertexes, 0, v1, 0, 3);
        System.arraycopy(vertexes, alignment, v2, 0, 3);
        System.arraycopy(vertexes, alignment * 2, v3, 0, 3);
    }

    @Override
    public BoundingBox getBoundingBox() {
        BoundingBox bbox = new BoundingBox();
        bbox.setBounds(this);
        return bbox;
    }

    @Override
    public float[] getCentroid() {
        return new float[] {
                (v1[0] + v2[0] + v3[0]) / 3f,
                (v1[1] + v2[1] + v3[1]) / 3f,
                (v1[2] + v2[2] + v3[2]) / 3f
        };
    }
}
