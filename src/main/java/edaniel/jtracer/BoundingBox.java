package edaniel.jtracer;

import com.jogamp.opengl.math.VectorUtil;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class BoundingBox extends OpenCLObject {

    @OpenCLField
    @Alignment(alignment = 16)
    float[] mins = new float[3];
    @OpenCLField
    @Alignment(alignment = 16)
    float[] maxs = new float[3];
    float[] size = new float[3];
    float[] center = new float[3];

    public BoundingBox() {
        setBounds(0);
        init();
    }

    float[] getCentroid() {
        return center;
    }

    void update() {
        updateCentroid();
        updateSize();
    }

    float[] diagonal() {
        return new float[] {maxs[0] - mins[0], maxs[1] - mins[1], maxs[2] - mins[2]};
    }

    float surfaceArea() {
        float[] diagonal = new float[3];
        VectorUtil.subVec3(diagonal, maxs, mins);
        return 2 * (diagonal[0] * diagonal[1] + diagonal[0] * diagonal[2]+ diagonal[1] * diagonal[2]);
    }

    void updateCentroid()
    {
        center[0] = (mins[0] + maxs[0]) / 2.0f;
        center[1] = (mins[1] + maxs[1]) / 2.0f;
        center[2] = (mins[2] + maxs[2]) / 2.0f;
    }

    void setBounds(float xMin, float yMin, float zMin,
                   float xMax, float yMax, float zMax)
    {
        mins[0] = xMin; mins[1] = yMin; mins[2] = zMin;
        maxs[0] = xMax; maxs[1] = yMax; maxs[2] = zMax;
        update();
    }

    void setBounds(Triangle t)
    {
        mins[0] = min(min(t.v1[0], t.v2[0]), t.v3[0]);
        mins[1] = min(min(t.v1[1], t.v2[1]), t.v3[1]);
        mins[2] = min(min(t.v1[2], t.v2[2]), t.v3[2]);

        maxs[0] = max(max(t.v1[0], t.v2[0]), t.v3[0]);
        maxs[1] = max(max(t.v1[1], t.v2[1]), t.v3[1]);
        maxs[2] = max(max(t.v1[2], t.v2[2]), t.v3[2]);
        update();
    }

    void updateSize()
    {
        size[0] = mins[0] < maxs[0] ? maxs[0] - mins[0] : mins[0] - maxs[0];
        size[1] = mins[1] < maxs[1] ? maxs[1] - mins[1] : mins[1] - maxs[1];
        size[2] = mins[2] < maxs[2] ? maxs[2] - mins[2] : mins[2] - maxs[2];
    }

    void setBounds(float val)
    {
        mins[0] = val; mins[1] = val; mins[2] = val;
        maxs[0] = val; maxs[1] = val; maxs[2] = val;
        center[0] = val; center[1] = val; center[2] = val;
        size[0] = 0.0f; size[1] = 0.0f; size[2] = 0.0f;
        // centery = val; centerz = val;
        update();
    }

    void merge(BoundingBox b) {
        mins[0] = mins[0] > b.mins[0] ? b.mins[0] : mins[0];
        mins[1] = mins[1] > b.mins[1] ? b.mins[1] : mins[1];
        mins[2] = mins[2] > b.mins[2] ? b.mins[2] : mins[2];

        maxs[0] = maxs[0] < b.maxs[0] ? b.maxs[0] : maxs[0];
        maxs[1] = maxs[1] < b.maxs[1] ? b.maxs[1] : maxs[1];
        maxs[2] = maxs[2] < b.maxs[2] ? b.maxs[2] : maxs[2];

        update();
    }

    Axis longestAxis() {
        if (size[0] > size[1] && size[0] > size[2])
            return Axis.X_AXIS;
        if (size[1] > size[0] && size[1] > size[2])
            return Axis.Y_AXIS;
        return Axis.Z_AXIS;
    }

    public enum Axis {
        X_AXIS(0),
        Y_AXIS(1),
        Z_AXIS(2);

        public int axis;

        Axis(int axis) {
            this.axis = axis;
        }
    }

}
