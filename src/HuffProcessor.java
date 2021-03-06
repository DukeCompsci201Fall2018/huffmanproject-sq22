import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in); //determine frequency of each 8-bit char
		HuffNode root = makeTreeFromCounts(counts); //use frequencies to create Huffman tree
		String[] codings = makeCodingsFromTree(root); //use tree to create encodings

		//write to compressed file - header then each chunk
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	/**
	 * Helper method to determine frequency of characters
	 * @param in BitInputStream of file to be compressed
	 * @return int array freq storing frequencies of each char
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1; //one occurrence of PSEUDO_EOF
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			freq[val] = freq[val] + 1;
		}
		return freq;
	}

	/**
	 * Helper method to make a tree based on the frequency of characters
	 * @param counts int[] that stores frequencies of each char
	 * @return Huffman tree built such that least frequent characters are
	 * in the furthest leaves from the node
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i <= ALPH_SIZE; i++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		} //add nonzero frequency values to priority queue
		if (myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes\n",  pq.size());
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	/**
	 * Helper method to add encodings of each character to an array,
	 * calls another helper method to recurse through tree.
	 * @param root tree created by makeTreeFromCounts
	 * @return array of encodings
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	/**
	 * Recursive helper method to travel through tree and find encodings of each leaf
	 * @param root tree created by makeTreeFromCounts
	 * @param path represents what have already traveled through, "0" for left, "1" for right
	 * @param encodings array of encodings found when reach leaf
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) { //base case, leaf
			encodings[root.myValue] = path;
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n",  root.myValue, path);
			}
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	/**
	 * Helper method to write sequence representing tree
	 * to read when decompressing.
	 * Uses recursive call to do a pre-order traversal.
	 * @param root tree created by makeTreeFromCounts
	 * @param out BitOutputStream to write to output file
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft != null || root.myRight != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}

	/**
	 * Helper method to write compressed file
	 * @param codings String array previously created of compressed
	 * encodings for each character
	 * @param in BitInputStream to get information to read
	 * @param out BitOutputStream to write to output file
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int chunk = in.readBits(BITS_PER_WORD);
			if (myDebugLevel >= DEBUG_HIGH) System.out.print(chunk + " ");
			if (chunk == -1) break;
			String code = codings[chunk];
			if (myDebugLevel >= DEBUG_HIGH) System.out.println(code);
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		if (bits == -1) {
			throw new HuffException("reading bits failed");
		}

		HuffNode root = readTreeHeader(in); //readTreeHeader should read tree to decompress
		readCompressedBits(root, in, out); //readCompressedBits should read from
		//compressed file and traverse paths of root and write leaf values to output file
		out.close();
	}

	/**
	 * Helper method to read tree used in compression/decompression.
	 * @param in BitInputStream of tree
	 * @return HuffNode that stores the values associated with each 'character'
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int oneBit = in.readBits(1);
		if (oneBit == -1) {
			throw new HuffException("reading bits failed");
		}
		if (oneBit == 0) { //bc if equals 1 then finished
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(9);
			return new HuffNode(value,0,null, null);
		}
	}

	/**
	 * Helper method reads the bits one at a time and writes to the
	 * output stream.
	 * @param root HuffNode returned by readTreeHeader
	 * @param in BitInputStream of compressed file to be read
	 * @param out BitOutputStream to write decompressed information to output file
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int oneBit = in.readBits(1);
			if (oneBit == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (oneBit == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}