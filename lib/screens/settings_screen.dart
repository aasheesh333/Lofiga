import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';
import 'dart:ui'; // For BackdropFilter

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Stack(
        children: [
           // Ambient Background Glow - Settings Specific
           Positioned(
             bottom: -100,
             left: -50,
             child: Container(
               width: 400,
               height: 400,
               decoration: BoxDecoration(
                 shape: BoxShape.circle,
                 color: const Color(0xFF3DF5E6).withOpacity(0.05), // Cyan tint
                 backgroundBlendMode: BlendMode.screen,
               ),
             ),
           ),
           BackdropFilter(
             filter: ImageFilter.blur(sigmaX: 60, sigmaY: 60),
             child: Container(color: Colors.transparent),
           ),

           SafeArea(
             bottom: false,
             child: Column(
               children: [
                 // Header
                 Padding(
                   padding: const EdgeInsets.all(24.0),
                   child: Row(
                     children: [
                       Text(
                         'Settings',
                         style: GoogleFonts.splineSans(
                           fontSize: 28,
                           fontWeight: FontWeight.bold,
                           color: Colors.white,
                         ),
                       ),
                     ],
                   ),
                 ),

                 Expanded(
                   child: ListView(
                     padding: const EdgeInsets.symmetric(horizontal: 24.0),
                     children: [
                       // Profile Section
                       Container(
                         padding: const EdgeInsets.all(20),
                         decoration: BoxDecoration(
                           color: Colors.white.withOpacity(0.05),
                           borderRadius: BorderRadius.circular(24),
                           border: Border.all(color: Colors.white10),
                         ),
                         child: Row(
                           children: [
                             Container(
                               width: 60, height: 60,
                               decoration: BoxDecoration(
                                 shape: BoxShape.circle,
                                 color: Theme.of(context).primaryColor,
                                 gradient: LinearGradient(
                                   colors: [Theme.of(context).primaryColor, Colors.blue],
                                   begin: Alignment.topLeft,
                                   end: Alignment.bottomRight,
                                 ),
                               ),
                               child: const Icon(Icons.person, color: Colors.white, size: 30),
                             ),
                             const SizedBox(width: 16),
                             Column(
                               crossAxisAlignment: CrossAxisAlignment.start,
                               children: [
                                 Text('Lofi Creator', style: GoogleFonts.splineSans(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white)),
                                 Text('Free Plan', style: GoogleFonts.splineSans(color: Theme.of(context).colorScheme.secondary, fontSize: 12, fontWeight: FontWeight.bold)),
                               ],
                             ),
                             const Spacer(),
                             IconButton(onPressed: (){}, icon: const Icon(Icons.edit, color: Colors.white54)),
                           ],
                         ),
                       ),

                       const SizedBox(height: 32),
                       
                       _buildSectionTitle('GENERAL'),
                       const SizedBox(height: 16),
                       _buildSettingItem(context, 'Audio Quality', 'High (320kbps)', Icons.graphic_eq),
                       _buildSettingItem(context, 'Export Path', '/Music/Lofiga', Icons.folder_open),
                       _buildSettingItem(context, 'Theme', 'Dark Mode', Icons.dark_mode, isSwitch: true),

                       const SizedBox(height: 32),

                       _buildSectionTitle('ABOUT'),
                       const SizedBox(height: 16),
                       _buildSettingItem(context, 'Version', '1.0.0 (Beta)', Icons.info_outline, onTap: () {
                         ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Lofiga v1.0.0 is up to date!')));
                       }),
                       _buildSettingItem(context, 'Privacy Policy', '', Icons.privacy_tip, onTap: () => _launchURL('https://example.com/privacy')),
                       _buildSettingItem(context, 'Terms of Service', '', Icons.description, onTap: () => _launchURL('https://example.com/terms')),

                       const SizedBox(height: 50),
                       
                       Center(
                         child: Text(
                           'Made with ❤️ for Lofi Lovers',
                           style: GoogleFonts.splineSans(color: Colors.white24, fontSize: 12),
                         ),
                       ),
                       const SizedBox(height: 100),
                     ],
                   ),
                 ),
               ],
             ),
           ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: GoogleFonts.splineSans(
        fontSize: 12,
        fontWeight: FontWeight.bold,
        letterSpacing: 2,
        color: Colors.white54,
      ),
    );
  }

  Future<void> _launchURL(String urlString) async {
    final Uri url = Uri.parse(urlString);
    if (!await launchUrl(url)) {
      debugPrint('Could not launch \$url');
    }
  }

  Widget _buildSettingItem(BuildContext context, String title, String subtitle, IconData icon, {bool isSwitch = false, VoidCallback? onTap}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        onTap: onTap ?? () {
           if (!isSwitch && subtitle.isNotEmpty) {
               ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('\$title setting coming soon!')));
           }
        },
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: Colors.white10),
        ),
        tileColor: const Color(0xFF231B2E), // Surface color
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.05),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: Colors.white70, size: 20),
        ),
        title: Text(title, style: GoogleFonts.splineSans(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.white)),
        subtitle: subtitle.isNotEmpty ? Text(subtitle, style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white38)) : null,
        trailing: isSwitch 
            ? Switch(
                value: true, 
                onChanged: (v){
                  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Dark theme is fixed for best experience!')));
                },
                activeColor: Theme.of(context).primaryColor,
                activeTrackColor: Theme.of(context).primaryColor.withOpacity(0.3),
              )
            : const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.white30),
      ),
    );
  }
}
