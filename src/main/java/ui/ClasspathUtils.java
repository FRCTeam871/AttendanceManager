package ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

public class ClasspathUtils {

    public static void loadJarDll(String name) throws IOException, URISyntaxException {
        InputStream in = ClasspathUtils.class.getClassLoader().getResourceAsStream(name);
        byte[] buffer = new byte[1024];
        int read = -1;
        //File temp = File.createTempFile(name.replace(".dll", ""), ".dll");
        File temp = new File(new File(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile(), name.split("/")[name.split("/").length-1]);

        System.out.println(temp.getAbsolutePath());
        temp.getParentFile().mkdirs();
        temp.createNewFile();
        FileOutputStream fos = new FileOutputStream(temp);

        while((read = in.read(buffer)) != -1){
            fos.write(buffer, 0, read);
        }
        fos.close();
        in.close();

        temp.deleteOnExit();

        System.load(temp.getAbsolutePath());
    }

}
