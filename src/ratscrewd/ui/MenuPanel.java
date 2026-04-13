package ratscrewd.ui;

import ratscrewd.game.GameController;
import ratscrewd.util.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MenuPanel extends JPanel {
    private final JFrame parent;

    public MenuPanel(JFrame frame) {
        this.parent = frame;
        setLayout(null);
        setBackground(new Color(30, 30, 30));

        JLabel logo = new JLabel("Ratscrew\u2019d", SwingConstants.CENTER);
        logo.setFont(new Font("Brush Script MT", Font.BOLD, 72));
        logo.setForeground(new Color(255, 215, 0));
        logo.setBounds(0, 50, Constants.WINDOW_WIDTH, 80);
        add(logo);

        // Button Y positions shift down by 70 each time a save exists
        boolean hasSave = GameController.saveExists();
        int y = 200;

        JButton newGame = new JButton("New Game");
        newGame.setFont(new Font("SansSerif", Font.BOLD, 24));
        newGame.setBackground(new Color(70, 130, 180));
        newGame.setForeground(Color.WHITE);
        newGame.setBounds(Constants.WINDOW_WIDTH / 2 - 100, y, 200, 50);
        newGame.addActionListener(this::startGame);
        add(newGame);
        y += 70;

        if (hasSave) {
            JButton cont = new JButton("Continue Game");
            cont.setFont(new Font("SansSerif", Font.BOLD, 24));
            cont.setBackground(new Color(34, 139, 34));
            cont.setForeground(Color.WHITE);
            cont.setBounds(Constants.WINDOW_WIDTH / 2 - 100, y, 200, 50);
            cont.addActionListener(this::continueGame);
            add(cont);
            y += 70;
        }

        JButton howToPlay = new JButton("How to Play");
        howToPlay.setFont(new Font("SansSerif", Font.BOLD, 24));
        howToPlay.setBackground(new Color(100, 100, 100));
        howToPlay.setForeground(Color.WHITE);
        howToPlay.setBounds(Constants.WINDOW_WIDTH / 2 - 100, y, 200, 50);
        howToPlay.addActionListener(this::showHowToPlay);
        add(howToPlay);
        y += 70;

        JButton exit = new JButton("Exit");
        exit.setFont(new Font("SansSerif", Font.BOLD, 24));
        exit.setBackground(new Color(178, 34, 34));
        exit.setForeground(Color.WHITE);
        exit.setBounds(Constants.WINDOW_WIDTH / 2 - 100, y, 200, 50);
        exit.addActionListener(e -> System.exit(0));
        add(exit);
    }

    private void startGame(ActionEvent e) {
        GameController.deleteSaveFile();
        GamePanel gp = new GamePanel(new GameController());
        parent.getContentPane().removeAll();
        parent.add(gp);
        parent.revalidate();
        parent.repaint();
        gp.requestFocusInWindow();
    }

    private void continueGame(ActionEvent e) {
        try {
            GamePanel gp = new GamePanel(GameController.loadFromFile(), true);
            parent.getContentPane().removeAll();
            parent.add(gp);
            parent.revalidate();
            parent.repaint();
            gp.requestFocusInWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Could not load the save file.\n" + ex.getMessage(),
                    "Load Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showHowToPlay(ActionEvent e) {
        HowToPlayDialog dialog = new HowToPlayDialog(parent);
        dialog.setVisible(true);
    }
}