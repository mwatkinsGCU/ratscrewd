package ratscrewd;

import javax.swing.JFrame;
import ratscrewd.ui.MenuPanel;
import ratscrewd.util.Constants;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Ratscrew\u2019d");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.add(new MenuPanel(frame));
        frame.setVisible(true);
    }
}