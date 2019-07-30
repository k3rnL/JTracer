package edaniel.jtracer;


import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;

import javax.swing.*;

public class Window extends JFrame implements GLEventListener {

    private final GLProfile mProfile;
    private final GLJPanel mPanel;
    private final FPSAnimator mAnimator;

    Window(int x, int y) {
        setSize(x, y);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mProfile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(mProfile);
        capabilities.setDoubleBuffered(true);
        capabilities.setHardwareAccelerated(true);

        mPanel = new GLJPanel(capabilities);
        setContentPane(mPanel);
        revalidate();

        mPanel.addGLEventListener(this);

        mAnimator = new FPSAnimator(mPanel, 60, true);
        mAnimator.start();

        mAnimator.setUncaughtExceptionHandler(new GLAnimatorControl.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(GLAnimatorControl glAnimatorControl, GLAutoDrawable glAutoDrawable, Throwable throwable) {
                throwable.printStackTrace();
                System.exit(1);
            }
        });

        setResizable(false);

    }

    Window() {
        this(400,400);
    }

    public GLJPanel getGLJPanel() {
        return mPanel;
    }

    public void init(GLAutoDrawable glAutoDrawable) {
        System.out.println("OpenGL started on "+glAutoDrawable.getGL().glGetString(GL.GL_RENDERER)+
                            " with OpenGL "+glAutoDrawable.getGL().glGetString(GL.GL_VERSION));
    }

    public void dispose(GLAutoDrawable glAutoDrawable) {

    }

    float t = -0.01f;
    float v = 0;

    public void display(GLAutoDrawable drawable) {
        setTitle("FPS:"+mAnimator.getLastFPS());
        try {
            Thread.sleep(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void reshape(GLAutoDrawable glAutoDrawable, int i, int i1, int i2, int i3) {

    }
}
