package ratscrewd.util;

import ratscrewd.model.Card;
import ratscrewd.model.CardValue;
import ratscrewd.model.Suit;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches card images for efficient rendering.
 */
public class CardImageLoader {
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();
    private static BufferedImage cardBackImage = null;
    private static BufferedImage placeholderImage = null;
    
    /**
     * Loads the image for a specific card.
     * Images are cached after first load for performance.
     * 
     * @param card the card to get an image for
     * @return BufferedImage of the card, or a placeholder if not found
     */
    public static BufferedImage getCardImage(Card card) {
        String fileName = getCardFileName(card);
        
        // Check cache first
        if (imageCache.containsKey(fileName)) {
            return imageCache.get(fileName);
        }
        
        // Try to load the image
        try {
            String imagePath = "images/cards/" + fileName;
            File imageFile = new File(imagePath);
            
            if (imageFile.exists()) {
                BufferedImage image = ImageIO.read(imageFile);
                // Resize to match Constants.CARD_WIDTH and Constants.CARD_HEIGHT
                BufferedImage resized = resizeImage(image, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
                imageCache.put(fileName, resized);
                return resized;
            } else {
                System.err.println("Card image not found: " + imagePath);
                return getPlaceholderImage();
            }
        } catch (IOException e) {
            System.err.println("Error loading card image: " + fileName);
            e.printStackTrace();
            return getPlaceholderImage();
        }
    }
    
    /**
     * Gets the card back image (for face-down cards).
     * 
     * @return BufferedImage of the card back
     */
    public static BufferedImage getCardBackImage() {
        if (cardBackImage != null) {
            return cardBackImage;
        }
        
        try {
            String imagePath = "images/cards/card_back.png";
            File imageFile = new File(imagePath);
            
            if (imageFile.exists()) {
                BufferedImage image = ImageIO.read(imageFile);
                cardBackImage = resizeImage(image, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
                return cardBackImage;
            } else {
                System.err.println("Card back image not found: " + imagePath);
                return getPlaceholderCardBack();
            }
        } catch (IOException e) {
            System.err.println("Error loading card back image");
            e.printStackTrace();
            return getPlaceholderCardBack();
        }
    }
    
    /**
     * Converts a Card to its image file name.
     * Format: "value_of_suit.png" (e.g., "ace_of_spades.png")
     */
    private static String getCardFileName(Card card) {
        if (card.getValue() == CardValue.JOKER) {
            return "joker.png";
        }
        
        String value = card.getValue().name().toLowerCase();
        switch (value)
        {
        	case "two":
        		value = "2";
        		break;
        	case "three":
        		value = "3";
        		break;
        	case "four":
        		value = "4";
        		break;
        	case "five":
        		value = "5";
        		break;
        	case "six":
        		value = "6";
        		break;
        	case "seven":
        		value = "7";
        		break;
        	case "eight":
        		value = "8";
        		break;
        	case "nine":
        		value = "9";
        		break;
        	case "ten":
        		value = "10";
        		break;
        	case "jack":
        		value = "J";
        		break;
        	case "queen":
        		value = "Q";
        		break;
        	case "king":
        		value = "K";
        		break;
        	case "ace":
        		value = "A";
        		break;
        	default:
        		break;
        }
        String suit = card.getSuit().name().toLowerCase();
        return suit + "_" + value + ".png";
    }
    
    /**
     * Resizes an image to the specified dimensions.
     * Uses nearest-neighbor scaling for crisp pixel art.
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        
        // Use nearest-neighbor for crisp pixel art (no blurring)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();
        
        return resizedImage;
    }
    
    /**
     * Creates a placeholder image for missing card images.
     */
    private static BufferedImage getPlaceholderImage() {
        if (placeholderImage != null) {
            return placeholderImage;
        }
        
        placeholderImage = new BufferedImage(Constants.CARD_WIDTH, Constants.CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholderImage.createGraphics();
        
        // Draw a simple placeholder
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
        g.setColor(Color.RED);
        g.drawRect(0, 0, Constants.CARD_WIDTH - 1, Constants.CARD_HEIGHT - 1);
        g.drawString("?", Constants.CARD_WIDTH / 2 - 5, Constants.CARD_HEIGHT / 2);
        g.dispose();
        
        return placeholderImage;
    }
    
    /**
     * Creates a placeholder card back when image is missing.
     */
    private static BufferedImage getPlaceholderCardBack() {
        BufferedImage placeholder = new BufferedImage(Constants.CARD_WIDTH, Constants.CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        
        // Draw a simple blue back
        g.setColor(Color.BLUE.darker());
        g.fillRect(0, 0, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, Constants.CARD_WIDTH - 1, Constants.CARD_HEIGHT - 1);
        g.dispose();
        
        return placeholder;
    }
    
    /**
     * Clears the image cache (useful if you want to reload images).
     */
    public static void clearCache() {
        imageCache.clear();
        cardBackImage = null;
        placeholderImage = null;
    }
}