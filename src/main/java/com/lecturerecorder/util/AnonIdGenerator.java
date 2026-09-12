package com.lecturerecorder.util;

import com.lecturerecorder.repository.UserRepository;

import java.util.Random;

public class AnonIdGenerator {

    private static final Random RANDOM = new Random();

    // Generates something like "Student#4821", retrying on collision.
    public static String generate(UserRepository userRepository) {
        String candidate;
        do {
            int number = 1000 + RANDOM.nextInt(9000);
            candidate = "Student#" + number;
        } while (userRepository.existsByAnonId(candidate));
        return candidate;
    }
}
