package edaniel.jtracer;

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.math.VectorUtil;
import com.nativelibs4java.opencl.*;
import com.nativelibs4java.util.IOUtils;
import org.bridj.Pointer;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;

import static com.jogamp.opengl.GL.*;
import static com.nativelibs4java.opencl.CLContext.GLTextureTarget.Texture2D;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class JTracer implements GLEventListener, KeyListener, MouseMotionListener {

    CLContext mContext;
    CLImage2D mImageOutput;
    private CLQueue mQueue;

    private CLProgram mProgram;
    private CLKernel mKernel;

    private TextureDrawer mDrawer;
    private int mTexture;

    private HashMap<Integer, Boolean> mKeyState = new HashMap<>();

    // ray tracer parameter
    private float[]     mPosition = new float[]{0,1,-10};
    private float[]     mRotation = new float[]{0,0,0};

    private Scene mScene;
    private CLBuffer<Byte> mSphereIdsBuffer;
    private CLBuffer<Byte> mKDNodeBuffer;

    public static void main(String[] args) {
        Window window = new Window(1280, 720);
        JTracer tracer = new JTracer();
        window.setVisible(true);
        window.getGLJPanel().addGLEventListener(tracer);
        window.getGLJPanel().addKeyListener(tracer);
        window.addKeyListener(tracer);
        window.addMouseMotionListener(tracer);


//        TracerControl control = TracerControl.createTracerControlWindow();

//        while (true) {
//            window.getGLCanvas().display();
//            window.getGLCanvas().swapBuffers();
//        }
    }

    public void init(GLAutoDrawable glAutoDrawable) {
        glAutoDrawable.getContext().makeCurrent();
        mContext = JavaCL.createContextFromCurrentGL();
        System.out.println("OpenCL started on "+mContext.getDevices()[0].getPlatform().getName());

        GL4 gl = glAutoDrawable.getGL().getGL4();

        int[] texture = new int[1];
        gl.glGenTextures(1, IntBuffer.wrap(texture));
        gl.glBindTexture(GL_TEXTURE_2D, texture[0]);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1280, 720, 0, GL_RGBA, GL_FLOAT, null);
        mTexture = texture[0];



        mImageOutput = mContext.createImage2DFromGLTexture2D(CLMem.Usage.Output, Texture2D, texture[0], 0);

        // back to opencl stuff
        // create queue
        mQueue = mContext.createDefaultQueue();
        // create program and kernel
        String str = null;
        try {
            str = IOUtils.readTextClose(JTracer.class.getResourceAsStream("test.opencl"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        mProgram = mContext.createProgram(str);
        mProgram.addBuildOption("-O5");
        mProgram.addBuildOption("-cl-fast-relaxed-math");
        mKernel = mProgram.createKernel("test", mImageOutput);

        mDrawer = new TextureDrawer();
        mDrawer.setTextureToDraw(mTexture);

        glAutoDrawable.addGLEventListener(mDrawer);

        mScene = new Scene();
//        mScene.addObject(sphere);

        mPositionBuffer = mContext.createBuffer(CLMem.Usage.Input, Pointer.pointerToFloats(mPosition));
//        mPositionBuffer = mContext.createBuffer(CLMem.Usage.Input, Float.class, 8);
        mRotationBuffer = mContext.createBuffer(CLMem.Usage.Input, Pointer.pointerToFloats(mRotation));
                prepareScene();
    }

    public void dispose(GLAutoDrawable glAutoDrawable) {

    }

    Sphere sphere = new Sphere();
    CLBuffer<Byte> mObjectbuffer;
    CLBuffer<Byte> mObjectIndexBuffer;
    CLBuffer<Float> mPositionBuffer;
    CLBuffer<Float> mRotationBuffer;

    public void display(GLAutoDrawable glAutoDrawable) {
        update();
//        prepareScene();

        mPositionBuffer.write(mQueue, Pointer.pointerToFloats(mPosition), false);
        mRotationBuffer.write(mQueue, Pointer.pointerToFloats(mRotation), false);

        mKernel.setArg(1, System.currentTimeMillis());
        mKernel.setArg(2, mPositionBuffer);
        mKernel.setArg(3, mRotationBuffer);
        mKernel.setArg(4, mObjectbuffer);
        mKernel.setArg(5, mObjectIndexBuffer);
        mKernel.setArg(6, mScene.getScene().size());

        long start = System.currentTimeMillis();
//System.exit(1);
        mQueue.enqueueAcquireGLObjects(new CLMem[] {mImageOutput});
        CLEvent event = mKernel.enqueueNDRange(mQueue, new int[]{1280*720});
//        CLEvent event = mKernel.enqueueNDRange(mQueue, new int[]{1280*720});
        mQueue.enqueueReleaseGLObjects(new CLMem[] {mImageOutput});

        mQueue.finish();

        System.out.println("Computation time : "+ (System.currentTimeMillis() - start) + " nb triangle ="+mScene.getScene().size());
//        System.out.println("cc");
    }

    public void reshape(GLAutoDrawable glAutoDrawable, int i, int i1, int i2, int i3) {

    }

    float sPos = 0;
    float step = 0.5f;

    long last = System.currentTimeMillis();
    public void update() {
        long diff = System.currentTimeMillis()-last;
        last=System.currentTimeMillis();
        float delta = diff/16f;
        sPos+=step * diff/16f;
        if (Math.abs(sPos) > 50)
            step *= -1;
        sphere.setPosition(sPos,0,0);
        final int step = 1;

        // calculate directions vectors
        float horizontal_angle = mRotation[1];
        float vertical_angle = mRotation[0];
        float[] direction = new float[] {
                (float) (sin(horizontal_angle) * cos(vertical_angle)),
                (float) sin(-vertical_angle),
                (float) (cos(horizontal_angle) * cos(vertical_angle))};
        VectorUtil.normalizeVec3(direction);
        float[] right = new float[] {
                (float) sin(horizontal_angle - 3.14f / 2.0f),
                0,
                (float) cos(horizontal_angle - 3.14f / 2.0f)
        };
        VectorUtil.normalizeVec3(right);
        float[] up = new float[3];
        VectorUtil.crossVec3(up, direction, right);

        float[] deltaPosition = new float[3];
        if (mKeyState.getOrDefault(KeyEvent.VK_UP, false))
            VectorUtil.addVec3(deltaPosition, deltaPosition, direction);
        if (mKeyState.getOrDefault(KeyEvent.VK_DOWN, false))
            VectorUtil.subVec3(deltaPosition, deltaPosition, direction);
        if (mKeyState.getOrDefault(KeyEvent.VK_LEFT, false))
            VectorUtil.addVec3(deltaPosition, deltaPosition, right);
        if (mKeyState.getOrDefault(KeyEvent.VK_RIGHT, false))
            VectorUtil.subVec3(deltaPosition, deltaPosition, right);
        if (mKeyState.getOrDefault(KeyEvent.VK_PAGE_UP, false))
            VectorUtil.subVec3(deltaPosition, deltaPosition, up);
        if (mKeyState.getOrDefault(KeyEvent.VK_PAGE_DOWN, false))
            VectorUtil.addVec3(deltaPosition, deltaPosition, up);
        VectorUtil.scaleVec3(deltaPosition, deltaPosition, step * delta);
        VectorUtil.addVec3(mPosition, mPosition, deltaPosition);
    }

    private void prepareScene() {
        List<SceneObject> objects = mScene.getScene();
        long totalSize = 0;
        for (SceneObject sceneObject : objects) {
            totalSize += sceneObject.getPointer().getValidElements();
        }

        if (mObjectbuffer == null || mObjectbuffer.getByteCount() != totalSize) {
            mObjectbuffer = mContext.createByteBuffer(CLMem.Usage.Input, totalSize);
            mObjectIndexBuffer = mContext.createByteBuffer(CLMem.Usage.Input, objects.size()*8);
        }

        ByteBuffer objectBuffer = ByteBuffer.allocate((int) totalSize);
        ByteBuffer indexBuffer = ByteBuffer.allocate(objects.size() * 8);
        indexBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int offset = 0;
        for (int i = 0 ; i < objects.size() ; i++) {
            Pointer<Byte> pointer = objects.get(i).getPointer();
            objectBuffer.put((byte[])pointer.getArray());
            indexBuffer.putInt(offset);
            System.out.println("index object "+offset);
            offset += pointer.getValidBytes() / 4;
        }

        objectBuffer.position(0);
        mObjectbuffer.writeBytes(mQueue, 0, totalSize, objectBuffer, false);
        mObjectIndexBuffer.writeBytes(mQueue, 0, indexBuffer.capacity(), indexBuffer, false);
    }

    // Key event

    public void keyTyped(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {
        mKeyState.put(e.getKeyCode(), true);
//        System.out.println(
//        KeyEvent.getKeyText(e.getKeyCode()));
    }

    public void keyReleased(KeyEvent e) {
        mKeyState.remove(e.getKeyCode());
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    int lastX = -1;
    int lastY = -1;
    @Override
    public void mouseMoved(MouseEvent e) {
        if (lastX < 0) {
            lastX = e.getX();
            lastY = e.getY();
        }
        mRotation[1] += (e.getX() - lastX)*0.01;
        mRotation[0] += (e.getY() - lastY)*0.01;
        lastX = e.getX();
        lastY = e.getY();
    }
}
