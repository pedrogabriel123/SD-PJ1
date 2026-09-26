
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Servidor {

    private static final int PORTA = 5000;

    private static final int MAX_CLIENTS = 1000;

    private static final Set<Connection> conexoesAtivas =
            Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {

        try (ServerSocket servidor = new ServerSocket(PORTA)) {

            System.out.println("Servidor iniciado.");
            System.out.println("Porta: " + PORTA);

            System.out.println(
                    "Máximo de conexões simultâneas: "
                            + MAX_CLIENTS
            );

            System.out.println("Aguardando conexões...");

            while (true) {

                Socket socket = servidor.accept();

                Connection connection;

                synchronized (conexoesAtivas) {

                    if (conexoesAtivas.size() >= MAX_CLIENTS) {

                        System.out.println(
                                "Conexão recusada: capacidade máxima atingida."
                        );

                        try {

                            socket.getOutputStream().write(
                                    "FULL\n".getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

                            socket.close();

                        } catch (IOException e) {

                            System.out.println(
                                    "Erro ao recusar conexão: "
                                            + e.getMessage()
                            );
                        }

                        continue;
                    }

                    final Connection[] referencia =
                            new Connection[1];

                    try {

                        connection = new Connection(
                                socket,
                                () -> removerConexao(
                                        referencia[0]
                                )
                        );

                        referencia[0] = connection;

                        conexoesAtivas.add(connection);

                    } catch (IOException e) {

                        System.out.println(
                                "Erro ao criar conexão: "
                                        + e.getMessage()
                        );

                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }

                        continue;
                    }

                    System.out.println(
                            "Cliente conectado. Conexões ativas: "
                                    + conexoesAtivas.size()
                                    + "/" + MAX_CLIENTS
                    );
                }

                connection.iniciar();

                // Inicia as threads de leitura e escrita da conexão.
                connection.iniciar();

                // Informa ao cliente que a conexão foi aceita.
                connection.enviar("CONNECTED");
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro no servidor: " + e.getMessage()
            );
        }
    }

    private static void removerConexao(
            Connection connection
    ) {

        if (connection == null) {
            return;
        }

        synchronized (conexoesAtivas) {

            conexoesAtivas.remove(connection);

            System.out.println(
                    "Conexão encerrada. Conexões ativas: "
                            + conexoesAtivas.size()
                            + "/" + MAX_CLIENTS
            );
        }
    }
}