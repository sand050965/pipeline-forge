package edu.northeastern.cs7580.cicd.executionservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the asynchronous pipeline execution queue.
 *
 * <p>Declares the durable queue, exchange, and binding used to decouple
 * pipeline submission (HTTP endpoint) from pipeline execution (consumer).
 * The HTTP endpoint publishes a {@link
 * edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage}
 * to the queue and returns 202 immediately; the {@link
 * edu.northeastern.cs7580.cicd.executionservice.consumer.PipelineExecutionConsumer}
 * picks up the message and executes the pipeline asynchronously.</p>
 *
 * <h2>Topology</h2>
 * <pre>
 *   HTTP endpoint
 *       │  publish (routingKey = ROUTING_KEY)
 *       ▼
 *   pipeline.execution.exchange  (DirectExchange, durable)
 *       │  binding
 *       ▼
 *   pipeline.execution.queue     (durable)
 *       │  @RabbitListener
 *       ▼
 *   PipelineExecutionConsumer
 * </pre>
 *
 * <h2>Durability</h2>
 *
 * <p>Both the queue and the exchange are declared as durable so they survive
 * RabbitMQ broker restarts without losing enqueued messages.</p>
 *
 * <h2>Message Serialization</h2>
 *
 * <p>A {@link Jackson2JsonMessageConverter} is registered so that
 * {@link edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage}
 * objects are serialized to/from JSON automatically. The same converter is
 * set on the {@link RabbitTemplate} so publish and consume use identical
 * serialization.</p>
 */
@Configuration
public class RabbitMqConfig {

  /** Name of the durable queue that holds pending pipeline execution messages. */
  public static final String QUEUE_NAME = "pipeline.execution.queue";

  /** Name of the direct exchange that routes messages to {@link #QUEUE_NAME}. */
  public static final String EXCHANGE_NAME = "pipeline.execution.exchange";

  /** Routing key used when publishing and binding. */
  public static final String ROUTING_KEY = "pipeline.execution";

  /**
   * Declares the durable pipeline execution queue.
   *
   * <p>Messages remain in the queue across broker restarts because the queue
   * is durable. The queue is not auto-deleted when all consumers disconnect.</p>
   *
   * @return durable {@link Queue} named {@link #QUEUE_NAME}
   */
  @Bean
  public Queue pipelineExecutionQueue() {
    return QueueBuilder.durable(QUEUE_NAME).build();
  }

  /**
   * Declares the durable direct exchange.
   *
   * <p>A {@link DirectExchange} routes messages to queues whose binding key
   * exactly matches the message's routing key.</p>
   *
   * @return durable {@link DirectExchange} named {@link #EXCHANGE_NAME}
   */
  @Bean
  public DirectExchange pipelineExecutionExchange() {
    return new DirectExchange(EXCHANGE_NAME, true, false);
  }

  /**
   * Binds {@link #pipelineExecutionQueue()} to {@link #pipelineExecutionExchange()}
   * using {@link #ROUTING_KEY}.
   *
   * @param queue    the pipeline execution queue
   * @param exchange the pipeline execution exchange
   * @return the binding between the queue and exchange
   */
  @Bean
  public Binding pipelineExecutionBinding(Queue queue, DirectExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
  }

  /**
   * Registers a {@link Jackson2JsonMessageConverter} as the default message
   * converter for both the {@link RabbitTemplate} (publishing) and the
   * {@code @RabbitListener} container factory (consuming).
   *
   * <p>Spring Boot auto-configures the listener container factory to pick up
   * any {@link MessageConverter} bean in the context, so no additional
   * configuration is needed for the consumer side.</p>
   *
   * @return Jackson-based JSON message converter
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /**
   * Configures the {@link RabbitTemplate} to use the {@link #jsonMessageConverter()}.
   *
   * <p>Without this, {@code RabbitTemplate.convertAndSend} would use the default
   * Java serialization converter, producing binary messages that the
   * {@code @RabbitListener} JSON converter cannot deserialize.</p>
   *
   * @param connectionFactory the auto-configured AMQP connection factory
   * @return a {@link RabbitTemplate} with JSON serialization enabled
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter());
    return template;
  }
}
