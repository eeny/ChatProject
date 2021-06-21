import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatServerGUI_v3 extends JFrame implements ActionListener, WindowListener {
	JTextArea ta_chat, ta_user;
	JTextField tf;
	HashMap<String, PrintWriter> map;// 접속자 맵
	HashMap<Integer, String> map2;// 채팅방 리스트

	public ChatServerGUI_v3() {
		init();
		serverSetting();
	}

	void init() { // ui설정
		this.setSize(500, 500);
		this.setDefaultCloseOperation(3);
		this.setTitle("서버");
		ta_chat = new JTextArea("===채팅내용===\n");
		ta_user = new JTextArea("===접속자===\n");
		JScrollPane sp_chat = new JScrollPane(ta_chat);
		JScrollPane sp_user = new JScrollPane(ta_user);
		tf = new JTextField();
		tf.addActionListener(this);
		this.addWindowListener(this);
		this.add(sp_chat);
		this.add(sp_user, "East");
		this.add(tf, "South");
		this.setVisible(true);
	}

	void serverSetting() {// 서버관련
		try {
			ServerSocket server = new ServerSocket(5000);
			ta_chat.append("접속을 기다립니다.\n");
			map = new HashMap<>();
			map2 = new HashMap<>();

			while (true) {// 이론상 접속자를 계속 받을수 있게 계속 대기 하기위해서
				Socket socket = server.accept();
				// 접속을 하면 쓰레드 실행
				(new ChatThreadGUI(socket, ta_chat, ta_user, map, map2)).start();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new ChatServerGUI_v3();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// /k aaa
		if (tf.getText().indexOf("/k ") == 0) {
			kick(tf.getText().split(" ")[1]);
			tf.selectAll();
		} else if (tf.getText().indexOf("/quit") == 0) {// 전체 종료

			for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
				kick(entry.getKey());
			}

			System.exit(1);
		} else {
			ta_chat.append("[서버]:" + tf.getText() + "\n");
			broadcast("[서버]:" + tf.getText());
			tf.selectAll();
		}
	}

	void broadcast(String str) {
		// 접속한 모두에게 글시 보내기 - 왜? - 서버니까 당연하 다한테 보낼 수 있어야한다.
		// 주로 게임으로 친다면, 공지사항 게임내 전체쳇
		// 우리는 정보를 다 들고 있다. 누가? - map이 다 들고 있다.

		for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
			entry.getValue().println(str);
			entry.getValue().flush();
		} // map 안에있는 모든 PrintWriter=out 에게 글씨를 보냄 - 방송 - 전체 쳇
	}

	void kick(String id) {
		map.get(id).println("/quit");
		map.get(id).flush();
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowClosing(WindowEvent e) {
		for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
			kick(entry.getKey());
		}

		System.exit(1);
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

// 채팅 쓰레드
class ChatThreadGUI extends Thread {
	// 이쓰레드는 클라이언트로 부터 들어오는 내용을 ta_chat에다가 추가하는 일을 함.
	Socket socket;
	JTextArea ta_chat, ta_user;
	HashMap<String, PrintWriter> map;
	HashMap<Integer, String> map2;

	PrintWriter out;
	BufferedReader in;
	String id;

	public ChatThreadGUI(Socket socket, JTextArea ta_chat, JTextArea ta_user, HashMap<String, PrintWriter> map,
			HashMap<Integer, String> map2) {
		this.socket = socket;
		this.ta_chat = ta_chat;
		this.ta_user = ta_user;
		this.map = map;
		this.map2 = map2;
		// 여기서 생성하게 되면 ChatServerGUI의 내용을 사용하는게 아니라 다 새로 만들어짐
		// 우리는 처음에 만들어진 ChatServerGUI의 컴포넌트들을 공용으로 사용해야 하므로 받아온다.

		try {
			out = new PrintWriter(socket.getOutputStream());// 내부적으로 버퍼사용
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			id = in.readLine();
			ta_user.append(id + "\n");
			ta_chat.append("[" + id + "]님께서 접속하였습니다.\n");

			map.put(id, out);

			out.println("[서버] 접속을 환영합니다.");
			out.flush();

			broadcast("/list " + getUserList());
			// 클라이언트에서는 한명이 접속과 동시에 무조건 /list aaa,bbb,ccc,ddd
			// 이렇게 접속자 정보를 받음

			// 채팅방 목록 뿌리기
			// /roomList 방1,aaa,1,고유번호~방2,aaa,1,고유번호~방3,aaa,고유번호
			// int isExit = 0(roomexit), 1(join), 그 외 숫자는 아무거나
			int isExit = 2;
			broadcast("/roomList " + getRoomList("", isExit));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			String msg = null;
			boolean id_check = false;

			while ((msg = in.readLine()) != null) {

				if (msg.equals("/quit")) {
					break;

				} else if (msg.indexOf("/w ") == 0 || msg.indexOf("/ㅈ ") == 0) {
					// 전역변수 id는 귓속말 보내는 사람
					// id_w는 귓속말 받는 사람
					String id_w = msg.split(" ")[1];

					// 존재하는 아이디인지 체크
					for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
						if (id_w.equals(entry.getKey())) {
							id_check = true;
							System.out.println("?");
							break;
						} else {
							id_check = false;
						}
					}

					// 만약 자기 자신한테 보낸 경우는 아무 실행 안되게 처리함
					if (id.equals(id_w)) {

						// 존재하는 아이디인 경우 귓속말 실행
					} else if (id_check) {
						msg = msg.substring(4 + id_w.length());
						// 귓속말 받는 사람한테 귓속말 보내기
						map.get(id_w).println("[" + id + "님의 귓속말]:" + msg);
						map.get(id_w).flush();
						// 귓속말 보낸 사람한테도 귓속말 보내기
						map.get(id).println("[" + id_w + "님에게 귓속말]:" + msg);
						map.get(id).flush();
						// 서버에서도 텍스트 남기기
						ta_chat.append("[ " + id + "님이 " + id_w + "님에게 귓속말]:" + msg + "\n");

					} else {
						map.get(id).println(id_w + " 님이 없습니다.");
						map.get(id).flush();
					}

					/*
					 * // 선생님 코드 (split 버그 있음!) // 귓속말 보낸 사람한테도 귓속말 보내기
					 * map.get(id).println("[" + msg.split(" ")[1] + "님에게 귓속말]:"
					 * + msg.split(" ")[2]); map.get(id).flush();
					 * 
					 * // 귓속말 받는 사람한테 귓속말 보내기
					 * map.get(msg.split(" ")[1]).println("[" + id + "님의 귓속말]:"
					 * + msg.split(" ")[2]);
					 * map.get(msg.split(" ")[1]).flush();// 버퍼의 내용을 강제로 다 꺼내서
					 * 보내라
					 * 
					 * // 서버에서도 텍스트 남기기 ta_chat.append(id + "가 " +
					 * msg.split(" ")[1] + "에게 귀속말 : " + msg.split(" ")[2]);
					 */

					// 새로운 채팅방이 만들어진 경우
					// 이 때 msg = "newRoom/" + title + "," + id + ",1" + "," +
					// room_idx
				} else if (msg.split("/")[0].equals("newRoom")) {
					String listTmp_room = msg.split("/")[1];
					String list_room[] = listTmp_room.split(",");
					ta_chat.append(id + "님이 " + list_room[0] + " 방을 만들었습니다.\n");
					System.out.println(msg);
					broadcast(msg);

					// 새로운 채팅방 정보 list에 넣기
				} else if (msg.split("/")[0].equals("newRoomIdx")) {
					// msg = newRoomIdx/room_idx/title,id,1,room_idx 이렇게 들어있음
					int roomIdx = Integer.parseInt(msg.split("/")[1]);
					String roomInfo = msg.split("/")[2];
					map2.put(roomIdx, roomInfo);

					System.out.println("map2 key = " + roomIdx);
					System.out.println("map2 value = " + roomInfo);
				
				} else if (msg.split("/")[0].equals("room")) {
					ta_chat.append("[" + id + "]:" + msg + "**\n");
					broadcast(msg + "/" + id);

				} else if (msg.split("/")[0].equals("join")) {
					int isExit = 1;
					broadcast("/roomList " + getRoomList(msg.split("/")[1], isExit));

				} else if (msg.split("/")[0].equals("roomexit")) {
					System.out.println(msg);
					int isExit = 0;
					broadcast("/roomList " + getRoomList(msg.split("/")[1], isExit));

				} else {
					// 서버에서 접속한 클라이언트에서 강제로 다시 서버에게 /quit 라고 보내라
					// 그러면 원래 있던 이 코드가 실행되면서 자동으로 종료됨.

					ta_chat.append("[" + id + "]:" + msg + "\n");
					broadcast("[" + id + "]:" + msg);
					// 다른 클라이언트가 서버로 글을 보내면
					// 서버가 읽어서 접속한 모두에게 그내용을 보냄
				}
			}
			// while이 빠져나오는순간 여기 끝. 더이상 내용못씀
			System.out.println(map.size() + "삭제전");// 1 이 맞나?
			// map("아이디", out);
			map.remove(id);// 한명 맵에서 지우기

			ta_chat.append("[" + id + "]님이 나가셨습니다.\n");
			// System.out.println(ta_user.getText());
			String tmp = ta_user.getText();
			tmp = tmp.replace(id + "\n", "");
			ta_user.setText(tmp);

			System.out.println(map.size() + "삭제후");// 1 이 맞나?
			// map은 클라이언트가 접속할대 마다
			// 그 클라이언트용 쓰레드(클라이언트가 보내는 글시씨 받는)가 생성

			// 나갈때 갱신
			broadcast("/list " + getUserList());
			// getUserList() <= 접속 한 map에서 key(아이디)만 다 가져오는 코드
			// remove로 map의 사람을 삭제 했기 때문에 다시 리스트를 전체에게 보내줌

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (in != null)
					in.close();
				if (out != null)
					out.close();
				if (socket != null)
					socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	void broadcast(String str) {
		for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
			entry.getValue().println(str);
			entry.getValue().flush();
		}
	}

	String getUserList() {
		String list = "";
		for (Map.Entry<String, PrintWriter> entry : map.entrySet()) {
			list += entry.getKey() + ",";
		} // aaa,bbb,ccc,

		if (!list.equals("")) {// 리스트에 한명이라도 있으면 => list의 내용이 "" 이 아니면
			list = list.substring(0, list.lastIndexOf(","));// 마지막 , 없애기
		}
		// aaa,bbb,ccc
		return list;
	}

	String getRoomList(String roomIdx, int isExit) {
		String str = "";

		// isExit = 0(roomexit), 1(join), 그 외 숫자는 아무거나
		if (isExit == 1) {// join의 경우
			if (!roomIdx.equals("")) {
				int row = Integer.parseInt(roomIdx);// 고유번호
				int a = 0;
				// String strrow = list.get(row);
				String strrow = map2.get(row);
				a = Integer.parseInt(strrow.split(",")[2]);
				a++;
				// list.remove(row);
				strrow = strrow.split(",")[0] + "," + strrow.split(",")[1] + "," + a + "," + roomIdx;
				// list.add(row, strrow);
				map2.put(row, strrow);
			}

		} else if (isExit == 0) {// roomexit의 경우
			if (!roomIdx.equals("")) {
				int row = Integer.parseInt(roomIdx);// 고유번호
				int a = 0;
				String strrow = map2.get(row);
				a = Integer.parseInt(strrow.split(",")[2]);
				--a;
				if (a <= 0) {// 인원수가 0이하라면 리스트에서 삭제하고 끝
					map2.remove(row);

				} else {
					map2.remove(row);
					strrow = strrow.split(",")[0] + "," + strrow.split(",")[1] + "," + a + "," + roomIdx;
					map2.put(row, strrow);
				}
			}
		}

		// 방1,aaa,1,고유번호~방2,aaa,1,고유번호~방3,aaa,고유번호 이런 형태
		for (Map.Entry<Integer, String> entry : map2.entrySet()) {
			str += entry.getValue() + "~";
		}

		if (!str.equals("")) {
			str = str.substring(0, str.lastIndexOf("~"));// 마지막 ~ 없애기
		} else {// 생성된 방이 없으면
			str = "방없음";
		}

		return str;
	}
}