package ratscrewd.util;

/**
 * Configuration constants for window layout and component sizes.
 */
public class Constants {
    public static final int WINDOW_WIDTH = 1024;
    public static final int WINDOW_HEIGHT = 768;
    public static final int SIDE_MARGIN = 20;
    public static final int BOTTOM_MARGIN = 40;
    public static final int CARD_WIDTH = 60;
    public static final int CARD_HEIGHT = 90;
    // Human hand sits above bottom margin
    public static final int HAND_Y_OFFSET = WINDOW_HEIGHT - CARD_HEIGHT - SIDE_MARGIN - BOTTOM_MARGIN;
    public static final int AI_HAND_Y_OFFSET = SIDE_MARGIN;
    public static final int CENTER_PILE_X = (WINDOW_WIDTH - CARD_WIDTH) / 2 - CARD_WIDTH;
    public static final int CENTER_PILE_Y = (WINDOW_HEIGHT - CARD_HEIGHT) / 2 - CARD_HEIGHT;
    public static final int MAX_HAND_SIZE = 7;
    public static final int SLAP_WARNING_TURNS = 10;
}
