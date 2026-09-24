package dev.libjadx.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.libjadx.project.NativeProjectDocument;

public record ServiceConfig(String bindAddress, int port, Path projectPath, List<Path> inputPaths, List<Path> allowedRoots,
		NativeProjectDocument nativeProject) {

	public static ServiceConfig parse(String[] args) throws IOException {
		return parse(args, System.getenv());
	}

	static ServiceConfig parse(String[] args, Map<String, String> environment) throws IOException {
		Path cliProject = null;
		Path configPath = null;
		List<Path> cliInputs = new ArrayList<>();
		List<Path> cliRoots = new ArrayList<>();
		String cliBind = null;
		Integer cliPort = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--help", "-h" -> {
					printHelp();
					return null;
				}
				case "--version" -> {
					System.out.println("LibJadx 0.1.0-SNAPSHOT");
					return null;
				}
				case "--project" -> cliProject = Path.of(value(args, ++i, "--project"));
				case "--input" -> cliInputs.add(Path.of(value(args, ++i, "--input")));
				case "--allowed-root" -> cliRoots.add(Path.of(value(args, ++i, "--allowed-root")));
				case "--bind" -> cliBind = value(args, ++i, "--bind");
				case "--port" -> cliPort = Integer.parseInt(value(args, ++i, "--port"));
				case "--config" -> configPath = Path.of(value(args, ++i, "--config"));
				default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
			}
		}
		Map<String, Object> fileConfig = readConfig(configPath);
		String envProject = blankToNull(environment.get("LIBJADX_PROJECT"));
		String envInput = blankToNull(environment.get("LIBJADX_INPUT"));
		Path project;
		List<Path> inputs;
		if (cliProject != null || !cliInputs.isEmpty()) {
			project = cliProject;
			inputs = List.copyOf(cliInputs);
		} else if (envProject != null || envInput != null) {
			project = pathValue(envProject);
			inputs = pathList(envInput, null);
		} else {
			project = pathValue(fileConfig.get("project"));
			inputs = pathList(null, fileConfig.get("input"));
		}
		List<Path> roots = new ArrayList<>(!cliRoots.isEmpty() ? List.copyOf(cliRoots)
				: pathList(environment.get("LIBJADX_ALLOWED_ROOT"), fileConfig.get("allowedRoots")));
		String bind = cliBind != null ? cliBind : stringValue(envOrFile(environment, "LIBJADX_BIND", fileConfig.get("bind")), "127.0.0.1");
		int port = cliPort != null ? cliPort : intValue(envOrFile(environment, "LIBJADX_PORT", fileConfig.get("port")), 18777);
		if (!"127.0.0.1".equals(bind)) {
			throw new IllegalArgumentException("Only loopback bind address 127.0.0.1 is currently supported");
		}
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("Port must be between 0 and 65535");
		}
		if ((project == null) == inputs.isEmpty()) {
			throw new IllegalArgumentException("Specify exactly one of --project PATH or one or more --input PATH options");
		}

		NativeProjectDocument nativeProject = null;
		Path projectPath = null;
		Path mappingsPath = null;
		List<Path> canonicalInputs;
		if (project != null) {
			projectPath = requireFile(project);
			nativeProject = NativeProjectDocument.open(projectPath);
			canonicalInputs = nativeProject.getInputFiles().stream().map(path -> canonicalExisting(path, "project input")).toList();
			mappingsPath = nativeProject.getMappingsPath();
			if (mappingsPath != null) {
				mappingsPath = canonicalExisting(mappingsPath, "mappings file");
			}
		} else {
			canonicalInputs = inputs.stream().map(path -> canonicalExisting(path, "input")).toList();
		}
		if (roots.isEmpty()) {
			if (projectPath != null) roots.add(projectPath.getParent());
			canonicalInputs.forEach(path -> roots.add(path.getParent()));
		}
		List<Path> canonicalRoots = roots.stream().map(path -> {
			try {
				return path.toRealPath();
			} catch (IOException e) {
				throw new IllegalArgumentException("Allowed root does not exist: " + path, e);
			}
		}).distinct().toList();
		List<Path> sensitivePaths = new ArrayList<>(canonicalInputs);
		if (projectPath != null) sensitivePaths.add(projectPath);
		if (mappingsPath != null) sensitivePaths.add(mappingsPath);
		for (Path path : sensitivePaths) {
			if (canonicalRoots.stream().noneMatch(path::startsWith)) {
				throw new IllegalArgumentException("Path is outside configured allowed roots: " + path);
			}
		}
		return new ServiceConfig(bind, port, projectPath, canonicalInputs, canonicalRoots, nativeProject);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> readConfig(Path configPath) throws IOException {
		if (configPath == null) return Map.of();
		Path canonical = configPath.toRealPath();
		Object value = new ObjectMapper(new YAMLFactory()).readValue(canonical.toFile(), Object.class);
		if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Config YAML root must be a mapping");
		return (Map<String, Object>) map;
	}

	private static Object envOrFile(Map<String, String> environment, String key, Object fileValue) {
		String envValue = environment.get(key);
		return envValue == null || envValue.isBlank() ? fileValue : envValue;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String stringValue(Object value, String defaultValue) {
		return value == null ? defaultValue : value.toString();
	}

	private static int intValue(Object value, int defaultValue) {
		if (value == null) return defaultValue;
		try {
			return Integer.parseInt(value.toString());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Port must be an integer", e);
		}
	}

	private static Path pathValue(Object value) {
		return value == null ? null : Path.of(value.toString());
	}

	private static List<Path> pathList(String environmentValue, Object fileValue) {
		if (environmentValue != null && !environmentValue.isBlank()) {
			return java.util.Arrays.stream(environmentValue.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
					.filter(part -> !part.isBlank()).map(Path::of).toList();
		}
		if (fileValue == null) return List.of();
		if (fileValue instanceof List<?> list) return list.stream().map(item -> Path.of(item.toString())).toList();
		return List.of(Path.of(fileValue.toString()));
	}

	private static String value(String[] args, int index, String option) {
		if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
		return args[index];
	}

	private static Path requireFile(Path path) throws IOException {
		Path canonical = canonicalExisting(path, "project");
		if (!Files.isRegularFile(canonical)) throw new IllegalArgumentException("Project must be a file: " + canonical);
		return canonical;
	}

	private static Path canonicalExisting(Path path, String label) {
		try {
			Path canonical = path.toRealPath();
			if (!Files.exists(canonical)) throw new IllegalArgumentException(label + " does not exist: " + path);
			return canonical;
		} catch (IOException e) {
			throw new IllegalArgumentException(label + " does not exist or cannot be resolved: " + path, e);
		}
	}

	private static void printHelp() {
		System.out.println("Usage: libjadx (--project PATH | --input PATH... ) [--config PATH] [--bind 127.0.0.1] [--port PORT] [--allowed-root PATH]");
		System.out.println("Starts one fixed project in a local headless Jadx service.");
		System.out.println("Config keys: project, input (list), bind, port, allowedRoots (list).");
		System.out.println("Environment overrides: LIBJADX_PROJECT, LIBJADX_INPUT, LIBJADX_BIND, LIBJADX_PORT, LIBJADX_ALLOWED_ROOT.");
	}
}
