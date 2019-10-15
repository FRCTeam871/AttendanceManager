package com.team871.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Frame {
    private final JFrame frame;
    private final JPanel panel;
    private final UICanvas canvas;

    private boolean hasFocus = false;
    private boolean fullscreen = false;

    private Dimension minimizedSize;

    public Frame() {
        frame = new JFrame("Attendance UI");
        panel = new JPanel();
        canvas = new UICanvas(1200, 800);

        setFullscreen(false);
        minimizedSize = frame.getSize();
        setFullscreen(true);
    }

    public void setFullscreen(boolean fullscreen) {
        frame.setVisible(false);
        frame.dispose();

        if (fullscreen) {
            Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
            panel.setPreferredSize(dim);
            canvas.resizeCanvas(dim.width, dim.height);

            frame.getContentPane().add(canvas);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setResizable(false);
        } else {
            Dimension dim = new Dimension(1200, 800);
            panel.setPreferredSize(minimizedSize != null ? minimizedSize : dim);
            panel.setSize(minimizedSize != null ? minimizedSize : dim);
            canvas.resizeCanvas(dim.width, dim.height);
            frame.setUndecorated(false);

            if (minimizedSize != null) {
                frame.setState(JFrame.NORMAL);
                frame.add(panel);
                frame.pack();
                frame.remove(panel);
                frame.setSize(minimizedSize.width - 10, minimizedSize.height - 10);
            } else {
                frame.add(panel);
                frame.pack();
                frame.remove(panel);
            }

            frame.getContentPane().add(canvas);
            frame.setResizable(false);

            frame.setState(JFrame.NORMAL);

            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        }

        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (e.getNewState() == 0) {
                    hasFocus = false;
                }
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                hasFocus = true;
            }
        });
        frame.setVisible(true);
        this.fullscreen = fullscreen;
    }

    public UICanvas getCanvas() {
        return canvas;
    }

    public void paint() {
        canvas.paint(canvas.getGraphics());
    }

    public void addKeyListener(KeyListener l) {
        canvas.addKeyListener(l);
    }

    public void addMouseWheelListener(MouseWheelListener l) {
        canvas.addMouseWheelListener(l);
    }

    public void addWindowListener(WindowListener l) {
        frame.addWindowListener(l);
    }

    public void setTitle(String title) {
        frame.setTitle(title);
    }

    public boolean hasFocus() {
        return hasFocus;
    }

    public void setVisible(boolean visible) {
        frame.setVisible(visible);
    }

    public String getTitle() {
        return frame.getTitle();
    }

    public boolean isFullscreen() {
        return fullscreen;
    }
}
