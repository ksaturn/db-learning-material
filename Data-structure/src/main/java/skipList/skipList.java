package skipList;

import java.util.Random;

public class skipList {

    private final int MAX_LEVEL = 16;
    private int levelCount = 1;

    private Random random = new Random();

    private Node head = new Node(MAX_LEVEL);

    public Node find(int val) {

        //begin with head node
        Node p = head;
        for (int l = levelCount - 1; l >= 0; l--) {
            while (p.forward[l] != null && p.forward[l].data < val) {
                p = p.forward[l];
            }
        }
        if (p.forward[0] != null && p.forward[0].data == val) {
            return p.forward[0];
        } else {
            return null;
        }

    }

    public void insert(int val) {
        Node p = head;
        //如果是第一个插入的结点那肯定是第一层
        int level = p.forward[0] == null ? 1 : randomLevel();

        //只允许每次新增加一层
        if (level > levelCount) {
            level = ++levelCount;
        }

        //最高处于第level层的结点应该创建一个指向大小为level的Node数组引用,用来记录各层的后继节点
        Node newNode = new Node(level);
        newNode.data = val;

        for (int l = levelCount - 1; l >= 0; l--) {
            while (p.forward[l] != null && p.forward[l].data < val) {
                p = p.forward[l];
            }
            //只有在level>l的层才能够插入新结点
            if (level > l) {
                if (p.forward[l] == null) {
                    p.forward[l] = newNode;
                } else {
                    Node next = p.forward[l];
                    p.forward[l] = newNode;
                    newNode.forward[l] = next;
                }
            }
        }
    }


    public void delete(int val) {
        //开一个levelCount大小的数组，记录每一层可能要删除的结点的前一个结点
        Node[] toDeleteNode = new Node[levelCount];
        Node p = head;
        for (int l = levelCount - 1; l >= 0; l--) {
            while (p.forward[l] != null && p.forward[l].data < val) {
                p = p.forward[l];
            }
            toDeleteNode[l] = p;
        }

        //当仅当原始链表上的p的后继节点等于val，即找到之后才返回
        if(p.forward[0]!=null&&p.forward[0].data==val){
            for (int l = levelCount - 1; l >= 0; l--) {
                if (toDeleteNode[l].forward[l] != null && toDeleteNode[l].forward[l].data == val) {
                    toDeleteNode[l].forward[l] = toDeleteNode[l].forward[l].forward[l];
                }
            }
        }
    }

    /**
     * 插入一个新结点的时候,需要决定其最高可能存在的层数
     *
     * @return
     */
    private int randomLevel() {
        int level = 1;
        for (int i = 1; i < MAX_LEVEL; i++) {
            if (random.nextInt() % 2 == 1) {
                level++;
            }
        }
        return level;
    }

    class Node {
        private int data;
        private Node[] forward;

        public Node(int level) {
            this.forward = new Node[level];
        }
    }
}


