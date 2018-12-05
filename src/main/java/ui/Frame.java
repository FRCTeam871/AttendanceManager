package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Frame implements WindowFocusListener {

    JFrame frame;
    JPanel panel;
    UICanvas canvas;

    boolean hasFocus = false;

    boolean fullscreen = false;

    public Frame(){
        init();
    }

    private void init(){
        setFullscreen(true);

//        Point loc = frame.getLocationOnScreen();
//        System.out.println(loc);
//
//        Dimension dim2 = dim;
//        new Thread(() -> {
//            try{
//                Thread.sleep(5000);
//                Robot r = new Robot();
//                r.mouseMove(loc.x + dim2.width/2, loc.y + dim2.height/2);
//                Thread.sleep(250);
//                r.mousePress(0);
//                Thread.sleep(500);
//                r.mouseRelease(0);
//            }catch(Exception e){
//                e.printStackTrace();
//            }
//        }).start();

    }

    public void setFullscreen(boolean fullscreen){
        if(frame != null) frame.dispose();
        if(fullscreen){
            Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
            if(frame == null) {
                frame = new JFrame("Attendance UI");
                panel = new JPanel();
            }
            panel.setPreferredSize(dim);
            canvas = new UICanvas(dim.width, dim.height);

//            frame.add(panel);
//            frame.pack();
//            frame.remove(panel);
            frame.getContentPane().add(canvas);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            frame.addWindowFocusListener(this);
            frame.setVisible(true);
        }else{
            Dimension dim = new Dimension(1200, 800);
            if(frame == null) {
                frame = new JFrame("Attendance UI");
                panel = new JPanel();
            }
            panel.setPreferredSize(dim);
            panel.setSize(dim);
            canvas = new UICanvas(dim.width, dim.height);
            frame.setUndecorated(false);

            frame.add(panel);
            frame.pack();
            frame.remove(panel);
            frame.getContentPane().add(canvas);
            frame.setState(JFrame.NORMAL);

            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            frame.addWindowFocusListener(this);
            frame.setVisible(true);
        }

        this.fullscreen = fullscreen;
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

    public void addMouseWheelListener(MouseWheelListener l){
        canvas.addMouseWheelListener(l);
    }

    public void addWindowListener(WindowListener l){
        frame.addWindowListener(l);
    }

    public void setTitle(String title){
        frame.setTitle(title);
    }

    @Override
    public void windowGainedFocus(WindowEvent e) {
        if(e.getNewState() == 0){
            hasFocus = false;
        }

        System.out.println("focus " + e.getNewState());
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
        hasFocus = true;
        System.out.println("gain");
    }

    public boolean hasFocus(){
        return hasFocus;
    }

    public void setVisible(boolean visible){
        frame.setVisible(visible);
    }

}
