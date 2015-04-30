package malt.next;

import jloda.util.Basic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Output writer that supports compression (.gz or .zip) and special file name STDOUT
 * <p/>
 * Daniel Huson, 8.2014
 */
public class OutputWriter extends BufferedWriter {
    private final boolean isFile;

    /**
     * constructor
     *
     * @param fileName
     * @throws java.io.IOException
     */
    public OutputWriter(String fileName) throws IOException {
        super(setup(fileName));
        isFile = (fileName == null || fileName.equalsIgnoreCase("STDOUT"));
    }

    /**
     * setup
     *
     * @param fileName
     * @return buffered writer
     * @throws java.io.IOException
     */
    private static BufferedWriter setup(String fileName) throws IOException {
        OutputStream outs;
        if (fileName == null || fileName.equalsIgnoreCase("STDOUT")) {
            outs = System.out;
        } else {
            outs = Basic.getOutputStreamPossiblyZIPorGZIP(fileName);
        }
        return new BufferedWriter(new OutputStreamWriter(outs), 10 * 1024 * 1024); // ten megabyte buffer, not sure whether this makes a difference
    }

    /**
     * close
     *
     * @throws java.io.IOException
     */
    public void close() throws IOException {
        super.close();
    }

    /**
     * does this write to a file (rather than to standard output)
     *
     * @return true, if writes to a file
     */
    public boolean isFile() {
        return isFile;
    }
}
