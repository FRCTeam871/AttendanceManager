package ui.imageprovider;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class FileImageProvider extends BufferedImageProvider {

    public FileImageProvider(File file) throws IOException {
        super(ImageIO.read(file));
    }

}
