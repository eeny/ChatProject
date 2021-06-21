import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class ChatClientGUI_v3 extends JFrame implements ActionListener, WindowListener, MouseListener {
	// UI 관련 변수
	JTable tblRoom, tblUser;
	JTextArea ta;
	JTextField tf;
	JButton btnSend, btnMake;
	JPanel pnlChat, pnlSend, pnlMain;
	String[] roomHeader = { "방제목", "방장", "현재인원", "고유번호" };
	String[][] roomContent = {};
	String[] userHeader = { "접속자" };
	String[][] userContent = {};
	DefaultTableModel modelRoom, modelUser;

	PopRoom poproom;
	// 채팅 관련 변수
	Socket socket;
	PrintWriter out;
	BufferedReader in;
	String id;
	String ip;

	// 테이블 관련 변수
	int selectedRow_user = -1;
	int selectedRow_room = -1;
	int room_idx;// 채팅방 고유번호(무조건 중복없음)

	ClientThreadGUI2 ct2 = null;

	public ChatClientGUI_v3() {
		init();
		clientSetting();
		// poproom = new PopRoom(this, "",false);
	}

	void init() {
		this.setDefaultCloseOperation(3);
		this.setSize(800, 500);
		this.addWindowListener(this);

		modelUser = new DefaultTableModel(userContent, userHeader);
		tblUser = new JTable(modelUser);
		tblUser.addMouseListener(this);
		JScrollPane spUser = new JScrollPane(tblUser);
		spUser.setPreferredSize(new Dimension(150, 500));
		this.add(spUser, "East");

		pnlMain = new JPanel(new GridLayout(0, 1));

		// modelRoom = new DefaultTableModel(roomContent, roomHeader);
		modelRoom = new DefaultTableModel(roomContent, roomHeader) {
			public boolean isCellEditable(int i, int c) {
				return false;
			}
		};

		tblRoom = new JTable(modelRoom);
		// 숨겨진 컬럼
		// tblRoom.getColumn("고유번호").setWidth(0);
		// tblRoom.getColumn("고유번호").setMinWidth(0);
		// tblRoom.getColumn("고유번호").setMaxWidth(0);

		tblRoom.addMouseListener(this);
		JScrollPane spRoom = new JScrollPane(tblRoom);
		pnlMain.add(spRoom);

		pnlChat = new JPanel(new BorderLayout());

		ta = new JTextArea();
		JScrollPane spTa = new JScrollPane(ta);
		pnlChat.add(spTa);

		pnlSend = new JPanel();
		tf = new JTextField(40);
		tf.addActionListener(this);// 엔터로 내용 보내기
		btnSend = new JButton("보내기");
		btnSend.addActionListener(this);// 버튼 눌러서 보내기
		btnMake = new JButton("방생성");
		btnMake.addActionListener(this);// 방생성 하기
		pnlSend.add(tf);
		pnlSend.add(btnSend);
		pnlSend.add(btnMake);
		pnlChat.add(pnlSend, "South");

		pnlMain.add(pnlChat);

		this.add(pnlMain);

		this.setVisible(true);

	}

	void clientSetting() { // 이전 코드 그대로 임.
		id = JOptionPane.showInputDialog("아이디 입력");
		ip = JOptionPane.showInputDialog("접속할 IP 입력", "127.0.0.1");

		if (id != null && ip != null) {
			this.setTitle("접속자 : " + id);

			if (id.equals("") || ip.equals("")) {
				System.exit(1);
			}

			try {
				socket = new Socket(ip, 5000);
				out = new PrintWriter(socket.getOutputStream());
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out.println(id);
				out.flush();

				// 서버가 보내는 내용을 계속 받기 위한 쓰레드 실행

				ct2 = new ClientThreadGUI2(in, out, ta, socket, tblUser, modelUser, tblRoom, modelRoom, room_idx, this);
				ct2.start();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {
			JOptionPane.showMessageDialog(this, "ID 또는 IP를 입력하지 않았습니다.");
			System.exit(1);
		}
	}

	void send() {

	}

	public static void main(String[] args) {
		new ChatClientGUI_v3();
	}

	void makeRoom() {
		// 1. pop창 만들기 - dialog로 구성
		// 2. 먼저 tblChat 테이블에 내용추가
		// 방정보 배열 만들기
		// modelRoom.addRow(rowData);
		String title = JOptionPane.showInputDialog("방제목을 입력하세요");

		if (title != null) {
			if (title.equals("")) {
				JOptionPane.showMessageDialog(this, "제목을 입력하지 않았습니다.");

			} else {
				// 고유번호 부여하기
				room_idx = makeRoomIdx();

				// out.println("newRoom/" + title + "," + id + ",1");
				out.println("newRoom/" + title + "," + id + ",1" + "," + room_idx);
				out.flush();

				out.println("newRoomIdx/" + room_idx + "/" + title + "," + id + ",1" + "," + room_idx);
				out.flush();

				// selectedRow_room = tblRoom.getRowCount();
				ct2.room_idx = room_idx;
				// poproom = new PopRoom(this, title, true, ct2);
				poproom = new PopRoom(this, title, true, ct2, room_idx);

				/*
				 * System.out.println("%%"); ct2.setRoom(poproom);
				 * System.out.println("$$");
				 */
			}

		} else {
			JOptionPane.showMessageDialog(this, "취소되었습니다.");
		}
	}

	int makeRoomIdx() {
		// 작성시간을 고유번호로 넘기기
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("hhmmss");
		String nowStr = now.format(dtf);
		room_idx = Integer.parseInt(nowStr);

		return room_idx;
	}

	void chatRoom() {
		// poproom = new PopRoom(this, tblRoom.getValueAt(selectedRow_room, 0) +
		// "", true, ct2);
		poproom = new PopRoom(this, tblRoom.getValueAt(selectedRow_room, 0) + "", true, ct2,
				Integer.parseInt(tblRoom.getValueAt(selectedRow_room, 3) + ""));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == btnMake) {
			makeRoom();

		} else {
			out.println(tf.getText());
			out.flush();

			if (tf.getText().equals("/quit")) {
				System.exit(0);
			}
			tf.selectAll();
		}
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowClosing(WindowEvent e) {
		out.println("/quit");
		out.flush();
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// tblRoom 클릭하는 경우
		if (e.getClickCount() == 2 && e.getSource() == tblRoom) {
			ct2.room_idx = Integer.parseInt(tblRoom.getValueAt(tblRoom.getSelectedRow(), 3) + "");
			selectedRow_room = tblRoom.getSelectedRow();
			// System.out.println(selectedRow_room);
			// out.println("join/" + selectedRow_room);
			// 고유번호로 join
			int roomIdx = Integer.parseInt(tblRoom.getValueAt(selectedRow_room, 3) + "");
			out.println("join/" + roomIdx);
			out.flush();
			chatRoom();
		}

		// tblUser 클릭하는 경우
		if (e.getSource() == tblUser) {
			// 선택한 줄 번호 가져오기 (선택안하면 -1)
			selectedRow_user = tblUser.getSelectedRow();

			if (id.equals(tblUser.getValueAt(selectedRow_user, 0))) {
				JOptionPane.showMessageDialog(this, "본인입니다.");

			} else {
				int result = JOptionPane.showConfirmDialog(this,
						tblUser.getValueAt(selectedRow_user, 0) + " 님에게 귓속말을 하시겠습니까?", "Confrim",
						JOptionPane.YES_NO_OPTION);

				if (result == JOptionPane.YES_OPTION) {
					tf.setText("/w " + tblUser.getValueAt(selectedRow_user, 0) + " ");
				}
			}
			// 텍스트필드에 포커스 주기
			tf.requestFocus();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}
}

class ClientThreadGUI2 extends Thread {
	BufferedReader in;
	PrintWriter out;
	JTextArea ta;
	Socket socket;
	JTable tblUser, tblRoom;
	DefaultTableModel modelUser, modelRoom;
	int room_idx;
	// ChatClientGUI_v3 main_client;
	PopRoom poproom;

	public ClientThreadGUI2(BufferedReader in, PrintWriter out, JTextArea ta, Socket socket, JTable tblUser,
			DefaultTableModel modelUser, JTable tblRoom, DefaultTableModel modelRoom, int selectedRow_room,
			ChatClientGUI_v3 main_client) {
		this.in = in;
		this.out = out;
		this.ta = ta;
		this.socket = socket;
		this.tblUser = tblUser;
		this.modelUser = modelUser;
		this.tblRoom = tblRoom;
		this.modelRoom = modelRoom;
		this.room_idx = room_idx;
		// this.main_client = main_client;
	}

	void setRoom(PopRoom poproom) {
		this.poproom = poproom;
		// System.out.println(111);
	}

	@Override
	public void run() {
		String msg = null;
		try {
			while ((msg = in.readLine()) != null) {
				// selectedRow_room = tblRoom.getRowCount()-1;
				System.out.println("받는 메시지 : " + msg + " : " + room_idx);
				if (msg.indexOf("/quit") == 0) {
					out.println("/quit");// 서버한테 quit 보내라
					out.flush();
					System.exit(1);

				} else if (msg.indexOf("/list ") == 0) {
					// list를 제외한 내용 aa,bb,cc
					String listTmp = msg.split(" ")[1];
					String list[] = listTmp.split(",");
					// 테이블에 넣기 작업 시작
					modelUser.setNumRows(0);// 모든 현재 내용 지우기
					for (int i = 0; i < list.length; i++) {
						modelUser.addRow(new String[] { list[i] });
					}

				} else if (msg.split("/")[0].equals("newRoom")) {
					String listTmp_room = msg.split("/")[1];
					String list_room[] = listTmp_room.split(",");
					modelRoom.addRow(list_room);

				} else if (msg.indexOf("/roomList ") == 0) {
					// 이때 msg => /roomList 방1,aaa,1,고유번호~방2,aaa,1,고유번호 형태
					// 만약 생성된 방이 없으면 msg => /roomList 방없음
					if (!msg.split(" ")[1].equals("방없음")) {// 방없음이 아닌경우
						// listTmp_room = 방1,aaa,1,고유번호~방2,aaa,1,고유번호 형태
						String listTmp_room = msg.split(" ")[1];

						String list_room[] = listTmp_room.split("~");

						modelRoom.setNumRows(0);// 초기화

						for (int i = 0; i < list_room.length; i++) {
							String title = list_room[i].split(",")[0];
							String owner = list_room[i].split(",")[1];
							String cnt = list_room[i].split(",")[2];
							String idx = list_room[i].split(",")[3];
							modelRoom.addRow(new String[] { title, owner, cnt, idx });
						}
						// 방없음일 경우
					} else {
						modelRoom.setNumRows(0);
					}

					// room으로 시작하는지 확인 후 고유번호가 같으면 출력 아니면 그냥 무시
					// 방채팅 기준은?
					// 1. room 으로 시작한다.
					// 2. 고유번호가 같아야 한다
					// 이 두가지라서 두번째  if 조건을 안 따지면 바로 else 전체채팅으로 출력된다!
				} else if (msg.split("/")[0].equals("room")) {
					
					// 시작이 room이고 고유번호가 같을 때
					if (msg.split("/")[1].equals(room_idx + "")) {
						System.out.println("roomidx : " + room_idx);
						String msg_room = msg.split("/")[2];
						poproom.ta_room.append("[" + msg.split("/")[3] + "]: " + msg_room + "\n");
					}

				} else {
					ta.append(msg + "\n");
					ta.setCaretPosition(ta.getDocument().getLength());// 글씨가 자동
					// 추가 될때
					// 스크롤바가
					// 안움직이는거
					// 수정
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (in != null)
					in.close();
				if (out != null)
					in.close();
				if (socket != null)
					in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

// 새로운 클래스
class PopRoom extends JDialog implements ActionListener, WindowListener {
	ChatClientGUI_v3 frame;
	String title;
	JTextArea ta_room;
	JTextField tf_room;
	boolean visible = false;

	// 지금 대화창의 고유번호
	int roomIdx;

	public PopRoom(ChatClientGUI_v3 frame, String title, boolean visible, ClientThreadGUI2 ct2, int roomIdx) {
		super(frame, true);
		this.frame = frame;
		this.title = title;
		this.visible = visible;
		this.roomIdx = roomIdx;
		ct2.setRoom(this);
		init();
	}

	void init() {
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		// 3 또는 EXIT_ON_CLOSE 로 해버리면 팝업창만 닫아도 전체가 다 닫혀버림
		// 실제 이 코드는 종료가 아니라 창만 안보이게 숨긴다고 보면됨.
		ta_room = new JTextArea();
		JScrollPane sp_room = new JScrollPane(ta_room);
		ta_room.setEditable(false);
		tf_room = new JTextField();
		tf_room.addActionListener(this);

		this.addWindowListener(this);

		this.add(sp_room);
		this.add(tf_room, "South");
		this.setTitle(title);
		this.setSize(500, 500);
		this.setVisible(visible);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		frame.out.println("room/" + roomIdx + "/" + tf_room.getText());
		System.out.println("room/" + roomIdx + "/" + tf_room.getText());
		frame.out.flush();
		/*
		 * frame.out.println("room/" + frame.selectedRow_room + "/" +
		 * tf_room.getText()); System.out.println("room/" +
		 * frame.selectedRow_room + "/" + tf_room.getText()); frame.out.flush();
		 */
		// ta_room.append(tf_room.getText());
		if (tf_room.getText().equals("/quit")) {
			System.exit(0);
		}
		tf_room.selectAll();

	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowClosing(WindowEvent e) {
		System.out.println("roomexit/" + roomIdx + "고유번호의 방에서 나감");
		frame.out.println("roomexit/" + roomIdx);
		frame.out.flush();
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}
}