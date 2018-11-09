package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;

public class Frame {

    JFrame frame;
    JPanel panel;
    UICanvas canvas;

    public Frame(){
        init();
    }

    private void init(){
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        dim = new Dimension(1200, 800);
        frame = new JFrame("Attendance UI");
        panel = new JPanel();
        panel.setPreferredSize(dim);
        canvas = new UICanvas(dim.width, dim.height);

        frame.add(panel);
        frame.pack();
        frame.remove(panel);
        frame.getContentPane().add(canvas);
        //frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        //frame.setUndecorated(true);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public UICanvas getCanvas(){
        return canvas;
    }

    public void paint(){
        canvas.paint(canvas.getGraphics());
    }

    public void addKeyListener(KeyListener l){
        canvas.addKeyListener(l);
    }

}
