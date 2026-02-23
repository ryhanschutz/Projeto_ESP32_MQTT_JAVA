package br.com.senai;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class AppPrensa {

    static final String BROKER           = "tcp://broker.hivemq.com:1883";
    static final String CLIENT_ID        = "JavaPrensa_" + System.currentTimeMillis();
    static final String TOPIC_TELEMETRIA = "senai/prensa/telemetria";
    static final String TOPIC_COMANDO    = "senai/prensa/comando";

    static MqttClient client;

    public static void main(String[] args) {
        try {
            client = new MqttClient(BROKER, CLIENT_ID);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            System.out.println("Conectando ao Broker...");
            client.connect(options);
            System.out.println("Conectado com sucesso!");

            // Assina o tópico de telemetria e processa cada mensagem recebida
            client.subscribe(TOPIC_TELEMETRIA, (topic, msg) -> {

                String payload = new String(msg.getPayload());
                System.out.println("\n----------------------------------");
                System.out.println("TELEMETRIA RECEBIDA: " + payload);

                // Extrai os valores do JSON recebido
                double temperatura = extrairValor(payload, "temperatura");
                double pressao     = extrairValor(payload, "pressao");

                System.out.printf("Temperatura: %.1f C | Pressao: %.0f%%%n",
                                   temperatura, pressao);

                // Decide o nível de segurança e publica o comando
                String comando = definirNivelSeguranca(temperatura, pressao);
                System.out.println("DECISAO: " + comando);
                publicarComando(comando);
            });

            System.out.println("Aguardando dados da prensa...");
            Thread.sleep(Long.MAX_VALUE);

        } catch (MqttException e) {
            System.out.println("Erro MQTT: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Programa encerrado.");
        }
    }

    // Define o nível de segurança com base nos limites da atividade
    static String definirNivelSeguranca(double temp, double pressao) {
        // Verifica crítico primeiro (condição mais grave)
        if (temp > 60.0 || pressao > 80.0) {
            return "VERMELHO";
        }
        // Verifica alerta
        if ((temp >= 45.0 && temp <= 60.0) || (pressao >= 50.0 && pressao <= 80.0)) {
            return "AMARELO";
        }
        // Tudo normal
        return "VERDE";
    }

    // Publica o comando no tópico que o ESP32 está escutando
    static void publicarComando(String comando) {
        try {
            MqttMessage mensagem = new MqttMessage(comando.getBytes());
            mensagem.setQos(1); // QoS 1 = entrega garantida pelo menos uma vez
            client.publish(TOPIC_COMANDO, mensagem);
            System.out.println("Comando publicado: " + comando);
        } catch (MqttException e) {
            System.out.println("Erro ao publicar: " + e.getMessage());
        }
    }

    // Extrai um valor numérico do JSON pelo nome da chave
    // Ex: extrairValor({"temperatura":25.5,"pressao":45}, "pressao") → 45.0
    static double extrairValor(String json, String chave) {
        try {
            String busca = "\"" + chave + "\":";
            int inicio = json.indexOf(busca) + busca.length();
            int fim = json.indexOf(",", inicio);
            if (fim == -1) fim = json.indexOf("}", inicio);
            return Double.parseDouble(json.substring(inicio, fim).trim());
        } catch (Exception e) {
            System.out.println("Erro ao extrair '" + chave + "': " + e.getMessage());
            return 0.0;
        }
    }
}