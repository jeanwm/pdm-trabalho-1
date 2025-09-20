import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {
    private static volatile boolean executando = true;

    public static void main(String[] args) {
        try {
            Socket socket     = new Socket("127.0.0.1", 12345);
            Scanner teclado   = new Scanner(System.in);
            PrintStream saida = new PrintStream(socket.getOutputStream());

            System.out.print("Digite seu nome: ");
            String nome = teclado.nextLine();
            saida.println(nome); 

            Thread receiverThread = new Thread(() -> {
                try {
                    InputStream is        = socket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                    while (executando) {
                        String mensagem = reader.readLine();

                        if (mensagem == null) {
                            System.out.println("Conexão encerrada pelo servidor");
                            executando = false;
                            break;
                        }

                        if (mensagem.startsWith("MENSAGEM")) {
                            String[] partes = mensagem.split(" ", 3);

                            if (partes.length >= 3) {
                                System.out.println(partes[1] + ": " + partes[2]);
                            }

                        } else if (mensagem.startsWith("ARQUIVO")) {
                            String[] partes = mensagem.split(" ", 4);

                            if (partes.length >= 4) {
                                String remetente = partes[1];
                                String nomeArquivo = partes[2];
                                long tamanho = Long.parseLong(partes[3]);
                                receberArquivo(socket, nomeArquivo, tamanho);
                                System.out.println("Arquivo recebido: " + nomeArquivo + " de " + remetente);
                            }

                        } else {
                            System.out.println(mensagem);
                        }
                    }

                } catch (IOException e) {
                    if (executando) {
                        System.out.println("Erro na conexão: " + e.getMessage());
                    }
                }
            });

            receiverThread.start();

            System.out.println("Conectado! Comandos disponíveis:");
            System.out.println("/send message <destinatario> <mensagem>");
            System.out.println("/send file <destinatario> <caminho>");
            System.out.println("/users - listar usuários");
            System.out.println("/sair - desconectar");

            while (executando && teclado.hasNextLine()) {
                String linha = teclado.nextLine();
                saida.println(linha);

                if (linha.equals("/sair")) {
                    executando = false;
                    System.out.println("Desconectando...");
                    break;
                }
            }

            executando = false;
            teclado.close();
            saida.close();
            socket.close();
            receiverThread.interrupt();

        } catch (IOException e) {
            System.out.println("Erro ao conectar: " + e.getMessage());
        }
    }

    private static void receberArquivo(Socket socket, String nomeArquivo, long tamanho) {
        try {
            InputStream is       = socket.getInputStream();
            FileOutputStream fos = new FileOutputStream(nomeArquivo);

            byte[] buffer = new byte[4096];
            long lidos    = 0;

            while (lidos < tamanho) {
                int bytesLidos = is.read(buffer, 0, (int) Math.min(buffer.length, tamanho - lidos));

                if (bytesLidos < 0) break;
                
                fos.write(buffer, 0, bytesLidos);
                lidos += bytesLidos;
            }

            fos.close();
            
        } catch (IOException e) {
            System.out.println("Erro ao receber arquivo: " + e.getMessage());
        }
    }
}