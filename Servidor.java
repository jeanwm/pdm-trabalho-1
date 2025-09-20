import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Scanner;

public class Servidor {
    private static final ConcurrentHashMap<String, PrintStream> clientes = new ConcurrentHashMap<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        ServerSocket servidor = new ServerSocket(12345);
        System.out.println("Servidor iniciado na porta 12345!");

        while (true) {
            Socket cliente = servidor.accept();
            System.out.println("Nova conexão: " + cliente.getInetAddress().getHostAddress());
            new Thread(new ClienteHandler(cliente)).start();
        }
    }

    private static void logConexao(String ip) {
        try (
            FileWriter fw     = new FileWriter("log.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out   = new PrintWriter(bw)
        ) {
            out.println(ip + " conectado em " + LocalDateTime.now().format(formatter));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClienteHandler implements Runnable {
        private Socket socket;
        private String nome;
        private Scanner entrada;
        private PrintStream saida;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new Scanner(socket.getInputStream());
                saida = new PrintStream(socket.getOutputStream());

                if (entrada.hasNextLine()) {
                    nome = entrada.nextLine();
                    System.out.println("Cliente " + nome + " conectado!");

                    synchronized (clientes) {
                        if (clientes.containsKey(nome)) {
                            saida.println("ERRO: Nome já em uso. Conexão encerrada.");
                            socket.close();
                            return;
                        }
                        clientes.put(nome, saida);
                    }

                    logConexao(socket.getInetAddress().getHostAddress());
                    saida.println("Conectado como: " + nome);

                    while (entrada.hasNextLine()) {
                        String mensagem = entrada.nextLine();
                        System.out.println(nome + " enviou: " + mensagem);
                        processarMensagem(mensagem);
                    }
                }
                
            } catch (IOException e) {
                System.out.println("Erro com cliente " + nome + ": " + e.getMessage());
            } finally {
                desconectar();
            }
        }

        private void processarMensagem(String mensagem) {
            if (mensagem.startsWith("/send message")) {
                String[] partes = mensagem.split(" ", 4);

                if (partes.length >= 4) {
                    String destinatario = partes[2];
                    String texto = partes[3];
                    enviarMensagem(destinatario, texto);
                }

            } else if (mensagem.startsWith("/send file")) {
                String[] partes = mensagem.split(" ", 4);

                if (partes.length >= 4) {
                    String destinatario = partes[2];
                    String caminhoArquivo = partes[3];
                    enviarArquivo(destinatario, caminhoArquivo);
                }

            } else if (mensagem.equals("/users")) {
                listarUsuarios();
            } else if (mensagem.equals("/sair")) {
                saida.println("Desconectando...");
            }
        }

        private void enviarMensagem(String destinatario, String texto) {
            PrintStream ps = clientes.get(destinatario);

            if (ps != null) {
                ps.println("MENSAGEM " + nome + " " + texto);
                saida.println("Mensagem enviada para " + destinatario);

            } else {
                saida.println("ERRO: Usuário " + destinatario + " não encontrado");
            }
        }

        private void enviarArquivo(String destinatario, String caminhoArquivo) {
            File arquivo = new File(caminhoArquivo);

            if (!arquivo.exists()) {
                saida.println("ERRO: Arquivo não encontrado");
                return;
            }

            PrintStream ps = clientes.get(destinatario);

            if (ps == null) {
                saida.println("ERRO: Usuário " + destinatario + " não encontrado");
                return;
            }

            try (FileInputStream fis = new FileInputStream(arquivo)) {
                ps.println("ARQUIVO " + nome + " " + arquivo.getName() + " " + arquivo.length());

                byte[] buffer = new byte[4096];
                int lidos;

                while ((lidos = fis.read(buffer)) > 0) {
                    ps.write(buffer, 0, lidos);
                }

                ps.flush();

                saida.println("Arquivo enviado para " + destinatario);

            } catch (IOException e) {
                saida.println("ERRO: Falha ao enviar arquivo");
                e.printStackTrace();
            }
        }

        private void listarUsuarios() {
            saida.println("Usuários conectados:");

            for (String usuario : clientes.keySet()) {
                saida.println("- " + usuario);
            }
        }

        private void desconectar() {
            if (nome != null) {
                clientes.remove(nome);
                System.out.println(nome + " desconectado!");
            }

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}