package ratscrewd.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal "How to Play" dialog explaining game rules and controls.
 * Replaces the old OptionsDialog.
 */
public class HowToPlayDialog extends JDialog {

    private static final Color BG         = new Color(22, 22, 32);
    private static final Color SECTION_BG = new Color(35, 35, 50);
    private static final Color HEADER_FG  = new Color(255, 215,   0);
    private static final Color BODY_FG    = new Color(210, 210, 225);
    private static final Color KEY_FG     = new Color(130, 200, 255);
    private static final Color ACCENT_FG  = new Color(180, 130, 255);
    private static final Color CLOSE_BG   = new Color(178,  34,  34);

    public HowToPlayDialog(JFrame parent) {
        super(parent, "How to Play - Ratscrew'd", true);
        setSize(800, 680);
        setLocationRelativeTo(parent);
        setResizable(true);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(16, 10, 10, 10));

        content.add(makeTitle("Ratscrew'd - How to Play"));
        content.add(Box.createVerticalStrut(12));

        content.add(makeSection("Overview",
            "Ratscrew'd is a fast-paced card game based on Egyptian Rat Screw. "
          + "You play one-on-one against an AI across multiple rounds. "
          + "Win a round and the next one starts with a tougher AI. "
          + "Lose a round and it's game over. "
          + "The goal is to survive as many rounds as possible."
        ));

        content.add(makeSection("The Deck and Dealing",
            "Each round uses a standard 54-card deck (52 cards plus 2 Jokers). "
          + "Both players are dealt 7 cards to start. "
          + "The rest of the deck sits in the centre as the draw pile. "
          + "After you play a card, your hand is automatically refilled from the centre pile. "
          + "Once the centre runs out, cards are refilled from your personal draw pile instead."
        ));

        content.add(makeSection("Playing Cards",
            "On your turn, select a card from your hand and press ENTER to play it face-up onto the discard pile. "
          + "You can select a card by clicking it or pressing its number key (1 through 7).\n\n"
          + "Faces and Aces rule: if a Jack, Queen, King, or Ace is played, "
          + "the other player must respond with another face card or Ace. "
          + "If they run out of chances without playing one, "
          + "the player who played the original face or Ace takes the whole discard pile "
          + "into their personal draw pile and gets the next turn."
        ));

        content.add(makeSection("Slapping",
            "Press SPACE at any time to slap the discard pile. "
          + "A slap is valid when the top two cards share the same value (a double), "
          + "or when the top card is a Joker. "
          + "A successful slap wins you the entire pile and earns +5 points.\n\n"
          + "A missed slap costs you 10 points and one card from your hand gets added to the pile. "
          + "Watch out: the AI slaps too, and it gets faster with every round."
        ));

        content.add(makeSection("Winning and Losing a Round",
            "A round ends one of two ways:\n\n"
          + "  * A player's hand and personal draw pile are both empty. That player loses.\n\n"
          + "  * Once the centre pile is gone, if no player gains 5 or more cards to their "
          + "personal pile across 10 consecutive play cycles, the player with the most total "
          + "cards wins. A warning shows up when 5 or fewer cycles remain.\n\n"
          + "If the round ends in a tie, a tiebreaker runs automatically: 3 cards are dealt "
          + "face-up to each player from a fresh deck and the point values are added up. "
          + "Whoever scores higher wins. If it's still a tie after several attempts, you win."
        ));

        content.add(makeSection("The Card Shop",
            "After winning a round you get to visit the Card Shop before the next round starts. "
          + "Here you can spend your points to permanently add cards to your deck. "
          + "Purchased cards get shuffled into the centre pile for every future round.\n\n"
          + "Six cards are available at a time. Click one to select it, then press ENTER to buy it. "
          + "If you want different options, hit Re-roll (costs 100 pts to start, +25 pts each time). "
          + "Press SPACE or click Continue when you're done shopping.\n\n"
          + "Card prices:\n"
          + "  * 2 through 5   -   25 pts\n"
          + "  * 6 through 10  -   40 pts\n"
          + "  * J, Q, K       -   70 pts\n"
          + "  * Ace           -   90 pts\n"
          + "  * Joker         -  130 pts"
        ));

        content.add(makeSection("Scoring",
            "You earn points throughout play:\n\n"
          + "  * Playing a number card  -  face value (2 through 10)\n"
          + "  * Playing a face card    -  10 points\n"
          + "  * Playing an Ace         -  11 points\n"
          + "  * Successful slap        -  +5 points\n"
          + "  * Missed slap            -  -10 points\n\n"
          + "Points carry over between rounds and are spent in the Card Shop."
        ));

        content.add(makeControlsSection());

        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        footer.setBackground(BG);
        JButton close = new JButton("Close");
        close.setFont(new Font("SansSerif", Font.BOLD, 15));
        close.setBackground(CLOSE_BG);
        close.setForeground(Color.WHITE);
        close.setFocusPainted(false);
        close.setBorder(BorderFactory.createEmptyBorder(8, 32, 8, 32));
        close.addActionListener(e -> dispose());
        footer.add(close);
        add(footer, BorderLayout.SOUTH);
    }

    private JLabel makeTitle(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 24));
        lbl.setForeground(HEADER_FG);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, lbl.getPreferredSize().height));
        return lbl;
    }

    private JPanel makeSection(String heading, String body) {
        // Outer panel uses BorderLayout so it always fills the full available width.
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECTION_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 60, 120), 1, true),
                        new EmptyBorder(10, 14, 10, 14))));

        JLabel header = new JLabel(heading);
        header.setFont(new Font("SansSerif", Font.BOLD, 15));
        header.setForeground(ACCENT_FG);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        panel.add(header, BorderLayout.NORTH);

        JTextArea ta = new JTextArea(body);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 13));
        ta.setForeground(BODY_FG);
        ta.setBackground(SECTION_BG);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setFocusable(false);
        ta.setBorder(null);
        panel.add(ta, BorderLayout.CENTER);

        return panel;
    }

    private JPanel makeControlsSection() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(SECTION_BG);
        outer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 60, 120), 1, true),
                        new EmptyBorder(10, 14, 10, 14))));

        JLabel header = new JLabel("Controls");
        header.setFont(new Font("SansSerif", Font.BOLD, 15));
        header.setForeground(ACCENT_FG);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));
        outer.add(header, BorderLayout.NORTH);

        String[][] rows = {
            { "1-7  /  Numpad 1-7", "Select the card at that position in your hand" },
            { "Click",              "Select a card in your hand or in the shop"      },
            { "ENTER",             "Play selected card / Buy selected shop card / Confirm" },
            { "SPACE",             "Slap the pile / Confirm prompt / Continue in shop"     },
            { "ESC",               "Return to the main menu at any time"            },
        };

        JPanel grid = new JPanel(new GridLayout(rows.length, 2, 12, 6));
        grid.setBackground(SECTION_BG);

        for (String[] row : rows) {
            JLabel key = new JLabel(row[0]);
            key.setFont(new Font("Monospaced", Font.BOLD, 13));
            key.setForeground(KEY_FG);
            grid.add(key);

            JLabel desc = new JLabel(row[1]);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
            desc.setForeground(BODY_FG);
            grid.add(desc);
        }

        outer.add(grid, BorderLayout.CENTER);
        return outer;
    }
}