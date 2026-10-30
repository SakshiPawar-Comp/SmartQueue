package com.smartqueue.notification.application;

import com.smartqueue.notification.domain.NotificationEventType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationTemplateService {

    public record RenderedTemplate(String title, String message) {}

    public RenderedTemplate render(NotificationEventType eventType, String language, Map<String, String> variables) {
        String lang = (language != null && !language.isBlank()) ? language.toLowerCase() : "en";
        if (!lang.equals("hi") && !lang.equals("mr")) {
            lang = "en";
        }

        String titleTemplate;
        String messageTemplate;

        switch (eventType) {
            case TOKEN_CREATED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "टोकन जारी किया गया";
                    messageTemplate = "आपका टोकन {tokenNumber} {queueName} के लिए बना दिया गया है। आपकी स्थिति {position} है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "टोकन जारी केले";
                    messageTemplate = "तुमचे टोकन {tokenNumber} {queueName} साठी तयार केले गेले आहे. तुमचे स्थान {position} आहे.";
                } else {
                    titleTemplate = "Token Issued";
                    messageTemplate = "Your token {tokenNumber} for {queueName} has been created. Your position is {position}.";
                }
            }
            case TOKEN_CALLED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "आपकी बारी है!";
                    messageTemplate = "टोकन {tokenNumber} को {queueName} में बुलाया गया है। कृपया काउंटर पर आगे बढ़ें।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "तुमची पाळी आहे!";
                    messageTemplate = "टोकन {tokenNumber} ला {queueName} मध्ये बोलावले आहे. कृपया काउंटरवर जा.";
                } else {
                    titleTemplate = "It's Your Turn!";
                    messageTemplate = "Token {tokenNumber} has been called for {queueName}. Please proceed to the counter.";
                }
            }
            case POSITION_NEAR -> {
                if (lang.equals("hi")) {
                    titleTemplate = "आपकी बारी नजदीक है";
                    messageTemplate = "टोकन {tokenNumber}: आपकी बारी जल्द आने वाली है। आपके आगे केवल {position} लोग हैं।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "तुमची पाळी जवळ आली आहे";
                    messageTemplate = "टोकन {tokenNumber}: तुमची पाळी लवकरच येत आहे. तुमच्या पुढे फक्त {position} व्यक्ती आहेत.";
                } else {
                    titleTemplate = "Your Turn is Approaching";
                    messageTemplate = "Token {tokenNumber}: Your turn is coming up soon. Position in queue: {position}.";
                }
            }
            case APPROACHING_TURN_5 -> {
                if (lang.equals("hi")) {
                    titleTemplate = "SmartQueue रिमाइंडर — आपसे आगे 5 लोग हैं";
                    messageTemplate = "कतार {queueName} में आपकी बारी नजदीक है। टोकन: {tokenNumber}, आपके आगे: {position} लोग। अनुमानित प्रतीक्षा समय: ~{estimatedWait} मिनट।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "SmartQueue रिमाइंडर — तुमच्या पुढे 5 व्यक्ती आहेत";
                    messageTemplate = "रांग {queueName} मध्ये तुमची पाळी जवळ आली आहे. टोकन: {tokenNumber}, तुमच्या पुढे: {position} व्यक्ती. अंदाजे प्रतीक्षा वेळ: ~{estimatedWait} मिनिटे.";
                } else {
                    titleTemplate = "SmartQueue Reminder — 5 People Ahead of You";
                    messageTemplate = "Your turn at {queueName} is getting closer. Token: {tokenNumber}. People ahead: {position}. Estimated waiting time: ~{estimatedWait} minutes." +
                                      (variables.containsKey("expectedTurnTime") ? " Expected turn: ~{expectedTurnTime}." : "") +
                                      (variables.containsKey("recommendedDepartureTime") ? " Recommended departure: {recommendedDepartureTime}. Expected arrival: {expectedArrivalTime}. Please keep an eye on SmartQueue because queue conditions can change." : "");
                }
            }
            case APPROACHING_TURN_3 -> {
                if (lang.equals("hi")) {
                    titleTemplate = "SmartQueue — आपकी बारी नजदीक है";
                    messageTemplate = "कतार {queueName} में आपकी बारी नजदीक है। टोकन: {tokenNumber}, आपके आगे: {position} लोग। अनुमानित प्रतीक्षा समय: ~{estimatedWait} मिनट।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "SmartQueue — तुमची पाळी जवळ येत आहे";
                    messageTemplate = "रांग {queueName} मध्ये तुमची पाळी जवळ आली आहे. टोकन: {tokenNumber}, तुमच्या पुढे: {position} व्यक्ती. अंदाजे प्रतीक्षा वेळ: ~{estimatedWait} मिनिटे.";
                } else {
                    titleTemplate = "SmartQueue — Your Turn Is Getting Close";
                    messageTemplate = "Your SmartQueue turn is approaching. Token: {tokenNumber}. People ahead: {position}. Estimated waiting time: ~{estimatedWait} minutes." +
                                      (variables.containsKey("expectedTurnTime") ? " Expected turn: ~{expectedTurnTime}." : "") +
                                      (variables.containsKey("recommendedDepartureTime") ? " Recommended departure: {recommendedDepartureTime}. Expected arrival: {expectedArrivalTime}. Please make your way to the branch if you have not already started traveling." : "");
                }
            }
            case TOKEN_HOLD -> {
                if (lang.equals("hi")) {
                    titleTemplate = "टोकन होल्ड पर रखा गया";
                    messageTemplate = "टोकन {tokenNumber} को अस्थायी रूप से होल्ड पर रखा गया है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "टोकन होल्डवर ठेवले";
                    messageTemplate = "टोकन {tokenNumber} तात्पुरते होल्डवर ठेवले आहे.";
                } else {
                    titleTemplate = "Token on Hold";
                    messageTemplate = "Token {tokenNumber} has been placed on hold.";
                }
            }
            case TOKEN_RESUMED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "टोकन पुनः शुरू हुआ";
                    messageTemplate = "टोकन {tokenNumber} प्रतीक्षा सूची में पुनः शुरू कर दिया गया है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "टोकन पुन्हा सुरू केले";
                    messageTemplate = "टोकन {tokenNumber} प्रतीक्षा यादीत पुन्हा सुरू करण्यात आले आहे.";
                } else {
                    titleTemplate = "Token Resumed";
                    messageTemplate = "Token {tokenNumber} has resumed waiting in the queue.";
                }
            }
            case TOKEN_SKIPPED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "टोकन छोड़ दिया गया";
                    messageTemplate = "टोकन {tokenNumber} को छोड़ दिया गया है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "टोकन वगळले";
                    messageTemplate = "टोकन {tokenNumber} वगळण्यात आले आहे.";
                } else {
                    titleTemplate = "Token Skipped";
                    messageTemplate = "Token {tokenNumber} was skipped.";
                }
            }
            case TOKEN_COMPLETED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "सेवा पूर्ण हुई";
                    messageTemplate = "टोकन {tokenNumber} की सेवा सफलतापूर्वक पूर्ण हो गई है। धन्यवाद!";
                } else if (lang.equals("mr")) {
                    titleTemplate = "सेवा पूर्ण झाली";
                    messageTemplate = "टोकन {tokenNumber} ची सेवा यशस्वीरित्या पूर्ण झाली आहे. धन्यवाद!";
                } else {
                    titleTemplate = "Service Completed";
                    messageTemplate = "Token {tokenNumber} has been completed. Thank you!";
                }
            }
            case TOKEN_CANCELLED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "टोकन रद्द किया गया";
                    messageTemplate = "टोकन {tokenNumber} रद्द कर दिया गया है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "टोकन रद्द केले";
                    messageTemplate = "टोकन {tokenNumber} रद्द करण्यात आले आहे.";
                } else {
                    titleTemplate = "Token Cancelled";
                    messageTemplate = "Token {tokenNumber} has been cancelled.";
                }
            }
            case QUEUE_CLOSED -> {
                if (lang.equals("hi")) {
                    titleTemplate = "कतार बंद कर दी गई";
                    messageTemplate = "{queueName} को आज के लिए बंद कर दिया गया है।";
                } else if (lang.equals("mr")) {
                    titleTemplate = "रांग बंद केली";
                    messageTemplate = "{queueName} आजसाठी बंद करण्यात आली आहे.";
                } else {
                    titleTemplate = "Queue Closed";
                    messageTemplate = "{queueName} has been closed for operations.";
                }
            }
            default -> {
                titleTemplate = "Queue Notification";
                messageTemplate = "Update for token {tokenNumber} in {queueName}.";
            }
        }

        String title = substitute(titleTemplate, variables);
        String message = substitute(messageTemplate, variables);

        return new RenderedTemplate(title, message);
    }

    private String substitute(String template, Map<String, String> variables) {
        if (template == null || variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }
}
