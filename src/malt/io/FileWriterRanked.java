/**
 * Copyright 2015, Daniel Huson
 * <p/>
 * (Some files contain contributions from other authors, who are then mentioned separately)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package malt.io;

import jloda.util.Basic;

import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * writes byte strings to a file in increasing order of rank
 * Daniel Huson, 8.2014
 */
public class FileWriterRanked {
    final public static int QUEUE_LENGTH = 1000000;

    private final static OutputItem SENTINEL = new OutputItem(0, null);

    private final ArrayBlockingQueue<OutputItem> outputQueue;
    private final ArrayBlockingQueue<OutputItem>[] threadSpecificWaitQueues;
    private final Writer writer;
    private final boolean isFile;
    private final StringBuilder fileFooter;

    private long nextRank;
    private boolean isClosing = false;
    private final CountDownLatch hasFinishedOutput = new CountDownLatch(1);

    private int queueHighWaterMark = 0;

    /**
     * constructor
     *
     * @param fileName
     * @param smallestRank value of first byte string to be written
     * @throws java.io.IOException
     */
    public FileWriterRanked(String fileName, final int numberOfThreads, int smallestRank) throws IOException {
        // one wait queue for each thread:
        threadSpecificWaitQueues = new ArrayBlockingQueue[numberOfThreads];
        for (int i = 0; i < threadSpecificWaitQueues.length; i++)
            threadSpecificWaitQueues[i] = new ArrayBlockingQueue<>(QUEUE_LENGTH);
        // the output queue:
        outputQueue = new ArrayBlockingQueue<>(QUEUE_LENGTH);

        // the output writer:
        OutputStream outs;
        if (fileName == null || fileName.equalsIgnoreCase("STDOUT")) {
            isFile = false;
            outs = System.out;
        } else {
            isFile = true;
            outs = Basic.getOutputStreamPossiblyZIPorGZIP(fileName);
        }
        writer = new BufferedWriter(new OutputStreamWriter(outs), 10 * 1024 * 1024); // ten megabyte buffer, not sure whether this makes a difference

        fileFooter = new StringBuilder();
        nextRank = smallestRank;

        // this thread collects output items in order from thread-specific waiting queues and places them on the output queue
        final Thread thread1 = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        boolean allEmpty = true;
                        for (ArrayBlockingQueue<OutputItem> queue : threadSpecificWaitQueues) {
                            OutputItem item = queue.peek();
                            while (item != null && item.rank == nextRank) {
                                if (allEmpty) {
                                    allEmpty = false;
                                    if (queue.size() > queueHighWaterMark)
                                        queueHighWaterMark = queue.size();
                                }
                                try {
                                    outputQueue.put(item);
                                } catch (InterruptedException e) {
                                    Basic.caught(e);
                                }
                                nextRank++;
                                item = queue.poll(); // don't use take(), don't want to block here...
                            }
                        }
                        if (allEmpty) {
                            if (isClosing) {
                                outputQueue.put(SENTINEL);
                                return;
                            } else
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    Basic.caught(e);
                                }
                        }
                    }
                } catch (InterruptedException ex) {
                    Basic.caught(ex);
                }
            }
        });
        thread1.start();

        // this thread writes output to file
        final Thread thread2 = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        OutputItem item = outputQueue.take();
                        if (item == SENTINEL) {
                            hasFinishedOutput.countDown();
                            return;
                        }
                        byte[][] strings = item.strings;
                        if (strings != null) {
                            for (byte[] string : strings) {
                                byte b = 0;
                                for (byte aString : string) {
                                    b = aString;
                                    if (b == 0)
                                        break; // zero-terminated byte string
                                    writer.write((char) b);
                                }
                                if (b != '\t') // if this ends on a tab, don't add new line, it is the query-name for BlastTab or SAM
                                    writer.write('\n');
                            }
                        }
                    }
                } catch (Exception ex) {
                    Basic.caught(ex);
                }
            }
        });
        thread2.start();
    }

    /**
     * close
     *
     * @throws java.io.IOException
     */
    public void close() throws IOException {
        isClosing = true;
        try {
            hasFinishedOutput.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        if (fileFooter.length() > 0)
            writer.write(fileFooter.toString());
        writer.flush();
        if (isFile)
            writer.close();
        /*
        if (queueHighWaterMark > 1) {
            System.err.println("(outputQueueHighWaterMark: " + queueHighWaterMark+")");
        }
        */
    }

    /**
     * Write byte strings to the out stream by rank.
     * By rank means that output is generated only when all output of lower output
     * has already been written
     * Does not make a copy of the byte arrays, so shouldn't recycle because unclear when this will be written
     * Then must not be overwritten
     *
     * @param rank    each call must have a different rank and no rank can be skipped
     * @param strings can be null
     */
    public void writeByRank(int threadId, long rank, byte[][] strings) {
        try {
            threadSpecificWaitQueues[threadId].put(new OutputItem(rank, strings));
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
    }

    /**
     * write a header and body by rank. By rank means that output is generated only when all output of lower output
     * has already been written
     * Does not make a copy of the byte arrays, so shouldn't recycle because unclear when this will be written
     *
     * @param rank
     * @param header
     * @param body
     */
    public void writeByRank(int threadId, long rank, byte[] header, byte[] body) {
        try {
            threadSpecificWaitQueues[threadId].put(new OutputItem(rank, new byte[][]{header, body}));
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
    }

    /**
     * skip a rank
     *
     * @param rank
     */
    public void skipByRank(int threadId, int rank) {
        try {
            threadSpecificWaitQueues[threadId].put(new OutputItem(rank, null));
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
    }

    /**
     * write this at the top of the file
     *
     * @param string
     * @throws java.io.IOException
     */
    public void writeFirst(String string) throws IOException {
        writer.write(string);
    }

    /**
     * write this at the end of the file
     *
     * @param string
     * @throws java.io.IOException
     */
    public void writeLast(String string) throws IOException {
        fileFooter.append(string);
    }
}

/**
 * output item consists of rank and bytes to write
 */
class OutputItem {
    long rank;
    byte[][] strings;

    OutputItem(long rank, byte[][] strings) {
        this.rank = rank;
        this.strings = strings;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("rank=").append(this.rank);
        if (strings != null) {
            for (byte[] string : strings) buf.append(Basic.toString(string));
        }
        return buf.toString();
    }
}
