package com.team871.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Frame implements WindowFocusListener {

    JFrame frame;
    JPanel panel;
    UICanvas canvas;

    boolean hasFocus = false;

    boolean fullscreen = false;

    Dimension minimizedSize;

    public Frame(){
        init();
    }

    private void init(){
        setFullscreen(false);
        minimizedSize = frame.getSize();
        //System.out.println("min " + minimizedSize);
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
        if(frame != null) {
            frame.dispose();
            frame.getContentPane().remove(canvas);
        }
        if(fullscreen){
            Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
            if(frame == null) {
                frame = new JFrame("Attendance UI");
                panel = new JPanel();
                canvas = new UICanvas(dim.width, dim.height);
            }
            panel.setPreferredSize(dim);
//            canvas = new UICanvas(dim.width, dim.height);
            canvas.resizeCanvas(dim.width, dim.height);

//            frame.add(panel);
//            frame.pack();
//            frame.remove(panel);
            frame.getContentPane().add(canvas);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setResizable(false);

            frame.addWindowFocusListener(this);
            frame.setVisible(true);
        }else{
            Dimension dim = new Dimension(1200, 800);
            if(frame == null) {
                frame = new JFrame("Attendance UI");
                canvas = new UICanvas(dim.width, dim.height);
            }
            panel = new JPanel();
            panel.setPreferredSize(minimizedSize != null ? minimizedSize : dim);
            panel.setSize(minimizedSize != null ? minimizedSize : dim);
//            canvas = new UICanvas(dim.width, dim.height);
            canvas.resizeCanvas(dim.width, dim.height);
            frame.setUndecorated(false);

            if(minimizedSize != null){
                frame.setState(JFrame.NORMAL);
                frame.add(panel);
                frame.pack();
                frame.remove(panel);
                frame.setSize(minimizedSize.width - 10, minimizedSize.height - 10);
            }else{
                frame.add(panel);
                frame.pack();
                frame.remove(panel);
            }


//            frame.add(panel);
//            frame.pack();
//            frame.remove(panel);
            frame.getContentPane().add(canvas);
            frame.setResizable(false);

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
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
        hasFocus = true;
    }

    public boolean hasFocus(){
        return hasFocus;
    }

    public void setVisible(boolean visible){
        frame.setVisible(visible);
    }

}
