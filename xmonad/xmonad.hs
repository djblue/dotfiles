import Graphics.X11.ExtraTypes.XF86
import XMonad
import XMonad.Config.Desktop
import XMonad.Hooks.DynamicLog
import XMonad.Hooks.ManageDocks
import XMonad.Layout.EqualSpacing
import XMonad.Util.EZConfig(additionalKeys)
import XMonad.Util.Paste
import XMonad.Util.Run(spawnPipe)
import Data.List

baseConfig = desktopConfig

main = xmonad =<< statusBar "xmobar" myPP toggleStructsKey (baseConfig
    { modMask = mod1Mask
    , terminal = "urxvt"

    , normalBorderColor = "#839496"
    , focusedBorderColor = "#dc322f"
    , borderWidth = 2

    -- topbar padding
    , manageHook = manageDocks <+> manageHook baseConfig
    , layoutHook = avoidStruts $ equalSpacing 16 0 0 0 $ layoutHook baseConfig
    , handleEventHook = docksEventHook <+> handleEventHook baseConfig

    } `additionalKeys`
    [ ((mod1Mask, xK_i), pasteSelection)

    -- emulate media keys for keyboards without them
    ,	((mod1Mask .|. shiftMask, xK_k), spawn "amixer -q sset Master 5%- unmute")
    ,	((mod1Mask .|. shiftMask, xK_m), spawn "amixer -q sset Master toggle")
    ,	((mod1Mask .|. shiftMask, xK_i), spawn "amixer -q sset Master 5%+ unmute")
    ,	((mod1Mask .|. shiftMask, xK_p), spawn "playerctl play-pause")
    ,	((mod1Mask .|. shiftMask, xK_j), spawn "playerctl previous")
    ,	((mod1Mask .|. shiftMask, xK_l), spawn "playerctl next")

    -- map screen backlight keys
    , ((0, xF86XK_MonBrightnessDown), spawn "xbacklight -dec 10")
    , ((0, xF86XK_MonBrightnessUp),   spawn "xbacklight -inc 10")

    -- map sounds media keys
    ,	((0, xF86XK_AudioLowerVolume), spawn "amixer -q sset Master 5%- unmute")
    ,	((0, xF86XK_AudioMute), spawn "amixer -q sset Master toggle")
    ,	((0, xF86XK_AudioRaiseVolume), spawn "amixer -q sset Master 5%+ unmute")
    ,	((0, xF86XK_AudioPlay), spawn "playerctl play-pause")
    ,	((0, xF86XK_AudioPrev), spawn "playerctl previous")
    ,	((0, xF86XK_AudioNext), spawn "playerctl next")

    ,	((mod1Mask, xK_p), spawn $ menu "dmenu_run")
    ,	((mod1Mask, xK_z), spawn $ menu "passmenu")

    , ((mod4Mask, xK_l), spawn "slock")
    ])

menu m = intercalate " " [ m
                         , "-fn 'xft:Droid Sans Mono Slashed for Powerline'"
                         , "-nb '#002b36'"
                         , "-nf '#657b83'"
                         , "-sb '#859900'"
                         , "-lh '32'"
                         ]

action id = intercalate "" [ "<action=`xdotool key alt+"
                           , id
                           , "`>"
                           , id
                           , "</action>"
                           ]

myPP = xmobarPP { ppCurrent = xmobarColor "#859900" ""
                , ppVisible = wrap "(" ")" . action
                , ppHidden = action
                , ppTitle = shorten 64
                , ppSep = " | "
                , ppLayout = const ""
                }

toggleStructsKey XConfig {XMonad.modMask = modMask} = (modMask, xK_b)

