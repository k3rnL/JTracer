package edaniel.jtracer;

import javax.swing.*;

public class TracerControl {
    private JList list1;
    private JPanel content;
    private JCheckBox realTimeCheckBox;
    private JButton renderButton;
    private JRadioButton cameraControlRadioButton;
    private JRadioButton objectControlRadioButton;

    private JFrame mFrame = new JFrame("Control");

    private TracerControl() {
        mFrame.setSize(content.getSize());
        mFrame.setContentPane(content);
        mFrame.setVisible(true);
    }

    public static TracerControl createTracerControlWindow() {
        TracerControl tc = new TracerControl();
        tc.mFrame.revalidate();
        return tc;
    }
}
