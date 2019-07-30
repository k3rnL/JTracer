package edaniel.jtracer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.nativelibs4java.util.IOUtils;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL.GL_TRIANGLE_FAN;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;

public class TextureDrawer implements GLEventListener {

    private final static float[] VERTEX = {
            1,  1, 0,
            -1,  1, 0,
            -1, -1, 0,
            1, -1, 0
    };

    private int             mVertexBuffer;
    private ShaderProgram   mProgram;

    private int             mTextureToDraw = -1;

    public void setTextureToDraw(int textureToDraw) {
        this.mTextureToDraw = textureToDraw;
    }

    public void init(GLAutoDrawable glAutoDrawable) {
        String vertexShaderCode = null;
        String fragmentShaderCode = null;
        try {
            vertexShaderCode = IOUtils.readTextClose(Window.class.getResourceAsStream("texture_draw.vert"));
            fragmentShaderCode = IOUtils.readTextClose(Window.class.getResourceAsStream("texture_draw.frag"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        GL2 gl2 = glAutoDrawable.getGL().getGL2();

        mProgram = new ShaderProgram();
        mProgram.add(new ShaderCode(GL_VERTEX_SHADER, 1, new CharSequence[][]{{vertexShaderCode}}));
        mProgram.add(new ShaderCode(GL_FRAGMENT_SHADER, 1, new CharSequence[][]{{fragmentShaderCode}}));
        mProgram.link(gl2, System.out);
        mProgram.validateProgram(gl2, System.out);

        int[] tmp = new int[1];
        gl2.glGenBuffers(1, IntBuffer.wrap(tmp));
        mVertexBuffer = tmp[0];

        gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, mVertexBuffer);
        gl2.glBufferData(GL2.GL_ARRAY_BUFFER, 4 * 4 * 3, FloatBuffer.wrap(VERTEX), GL3.GL_STATIC_DRAW);
    }

    public void dispose(GLAutoDrawable glAutoDrawable) {

    }

    public void display(GLAutoDrawable glAutoDrawable) {
        if (mTextureToDraw < 0)
            return;

        GL2 gl = glAutoDrawable.getGL().getGL2();

        mProgram.useProgram(gl, true);
        gl.glUseProgram(mProgram.program());
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, mVertexBuffer);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

        gl.glActiveTexture(0);
        gl.glBindTexture(GL_TEXTURE_2D, mTextureToDraw);
        int image_index_shader = gl.glGetUniformLocation(mProgram.program(), "image");
        gl.glUniform1i(image_index_shader, 0);

        gl.glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
    }

    public void reshape(GLAutoDrawable glAutoDrawable, int i, int i1, int i2, int i3) {

    }
}
